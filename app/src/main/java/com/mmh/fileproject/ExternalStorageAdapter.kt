package com.mmh.fileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mmh.fileproject.databinding.ItemPhotoBinding

class ExternalStorageAdapter(val onItemClick: (ExternalStoragePhoto) -> Unit) :
    ListAdapter<ExternalStoragePhoto, ExternalStorageAdapter.PhotoViewHolder>(DiffUtils()) {
    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: ExternalStoragePhoto) {
            with(binding) {
                photoImage.setImageURI(photo.contentUri)
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

    class DiffUtils : DiffUtil.ItemCallback<ExternalStoragePhoto>() {
        override fun areItemsTheSame(oldItem: ExternalStoragePhoto, newItem: ExternalStoragePhoto): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ExternalStoragePhoto, newItem: ExternalStoragePhoto): Boolean {
            return oldItem == newItem
        }
    }
}