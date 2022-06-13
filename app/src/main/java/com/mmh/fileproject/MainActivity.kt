package com.mmh.fileproject

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import androidx.recyclerview.widget.GridLayoutManager
import com.mmh.fileproject.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var readPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private var deletedPhotoUri: Uri? = null

    private val internalAdapter = InternalStoragePhotoAdapter(onItemClick = { photo -> adapterOnClick(photo) })
    private val externalAdapter = ExternalStorageAdapter(onItemClick = { photo -> externalAdapterOnClick(photo) })

    private fun adapterOnClick(photo: InternalStoragePhoto) {
        deleteInternalPhotos(photo.name)
        updateRecyclerViewFromInternal()
    }

    private fun externalAdapterOnClick(photo: ExternalStoragePhoto) {
        lifecycleScope.launch {
            deleteExternalPhotos(photo.contentUri)
            deletedPhotoUri = photo.contentUri
        }
        updateRecyclerViewFromExternal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.rvPrivatePhotos.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = internalAdapter
        }

        binding.rvExternalPhotos.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = externalAdapter
        }

        binding.camera.setOnClickListener {
            takePhoto.launch()
        }

        updateRecyclerViewFromInternal()
        updateRecyclerViewFromExternal()

        permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

            if (readPermissionGranted) {
                updateRecyclerViewFromExternal()
            } else {
                toast("Can't read files without permission")
            }
        }

        updateOrRequestPermissions()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deleteExternalPhotos(deletedPhotoUri ?: return@launch)
                    }
                }
                toast("Photo deleted!")
            } else {
                toast("Can't read photo without permission!")
            }
        }
    }

    private fun updateOrRequestPermissions () {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (!readPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun savePhotoToExternalStorage (filename: String, bmp: Bitmap?): Boolean {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // чтобы сохранить метаданные в фото
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp?.width)
            put(MediaStore.Images.Media.HEIGHT, bmp?.height)
        }
        return try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use { stream ->
                    if (!bmp?.compress(Bitmap.CompressFormat.JPEG, 100, stream)!!) {
                        throw IOException ("Couldn't save bitmap")
                    } else {
                        updateRecyclerViewFromExternal()
                    }
                }
            } ?: throw IOException ("Cannot create MediaStore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadExternalPhotos (): List<ExternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
            val photos = mutableListOf<ExternalStoragePhoto>()
             contentResolver.query(
                 collection,
                 projection,
                 null,
                 null,
                 "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
             ) ?.use { cursor ->
                 val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                 val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                 val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                 val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                 while (cursor.moveToNext()) {
                     val id = cursor.getLong(idColumn)
                     val displayName = cursor.getString(displayNameColumn)
                     val width = cursor.getInt(widthColumn)
                     val height = cursor.getInt(heightColumn)
                     val contentUri = ContentUris.withAppendedId(
                         MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                         id
                     )
                     photos.add(ExternalStoragePhoto(id, displayName, width, height, contentUri))
                 }
                 photos.toList()
             } ?: listOf()
        }
    }

    private fun updateRecyclerViewFromInternal(){
        lifecycleScope.launch {
            val internalList = loadInternalPhotos()
            internalAdapter.submitList(internalList)
        }
    }

    private fun updateRecyclerViewFromExternal(){
        lifecycleScope.launch {
            val externalList = loadExternalPhotos()
            externalAdapter.submitList(externalList)
        }
    }

    private val takePhoto  = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
       val isPrivate: Boolean = binding.isPrivateSwitch.isChecked
       val isSavedSuccessfully = when {
           isPrivate -> saveInternalPhotos(UUID.randomUUID().toString(), bmp)
           writePermissionGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), bmp)
           else -> false
       }
       if (isPrivate) {
           updateRecyclerViewFromInternal()
       }
       if (isSavedSuccessfully) {
           toast("Photo saved!")
       } else {
           toast("Failed to save photo")
       }
   }

    private fun deleteInternalPhotos(filename: String): Boolean{
        return try {
            toast("$filename is deleted")
            deleteFile(filename)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    private suspend fun deleteExternalPhotos(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // будет работать на api 28 и ниже. на 29 и выше выбросит SecurityException
                contentResolver.delete(photoUri, null, null)
            } catch (e: SecurityException) {
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }
                intentSender?.let { sender ->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(sender).build()
                    )
                }
            }
        }
    }

    private suspend fun loadInternalPhotos(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir?.listFiles()
            files?.filter { it.isFile && it.canRead() && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bmp)
            } ?: listOf()
        }
    }

    private fun saveInternalPhotos(filename: String, bmp: Bitmap?): Boolean {
        return try {
            openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                if (bmp != null) {
                    if (bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                       toast("Bitmap saved!")
                    } else {
                        throw IOException("Couldn't save bitmap")
                    }
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }
}