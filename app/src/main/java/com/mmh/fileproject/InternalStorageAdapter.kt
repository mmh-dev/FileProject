package com.mmh.fileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mmh.fileproject.databinding.ItemPhotoBinding

class InternalStoragePhotoAdapter(val onItemClick: (InternalStoragePhoto) -> Unit) :
    ListAdapter<InternalStoragePhoto, InternalStoragePhotoAdapter.PhotoViewHolder>(DiffUtils()) {
    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: InternalStoragePhoto) {
            with(binding) {
                photoImage.setImageBitmap(photo.bmp)
                root.setOnClickListener {
                    onItemClick(photo)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffUtils : DiffUtil.ItemCallback<InternalStoragePhoto>() {
        override fun areItemsTheSame(oldItem: InternalStoragePhoto, newItem: InternalStoragePhoto): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: InternalStoragePhoto, newItem: InternalStoragePhoto): Boolean {
            return oldItem == newItem
        }
    }
}