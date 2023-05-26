package com.gmail.spittelermattijn.sipkip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gmail.spittelermattijn.sipkip.databinding.ActivityPreviouslyUploadedBinding
import com.gmail.spittelermattijn.sipkip.databinding.ItemPreviouslyUploadedBinding
import com.gmail.spittelermattijn.sipkip.util.activity
import com.google.android.material.card.MaterialCardView

class PreviouslyUploadedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPreviouslyUploadedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: PreviouslyUploadedViewModel by viewModels()
        binding = ActivityPreviouslyUploadedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recyclerView = binding.recyclerviewPreviouslyUploaded
        val adapter = PreviouslyUploadedAdapter()
        recyclerView.adapter = adapter
        recyclerView.setBackgroundColor(MaterialCardView(this).strokeColorStateList!!.defaultColor)
        viewModel.items.observe(this) { adapter.submitList(it) }
    }

    class PreviouslyUploadedAdapter :
        ListAdapter<PreviouslyUploadedViewModel.Item, PreviouslyUploadedViewHolder>(object : DiffUtil.ItemCallback<PreviouslyUploadedViewModel.Item>() {

            override fun areItemsTheSame(oldItem: PreviouslyUploadedViewModel.Item, newItem: PreviouslyUploadedViewModel.Item): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: PreviouslyUploadedViewModel.Item, newItem: PreviouslyUploadedViewModel.Item): Boolean =
                oldItem == newItem
        }) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviouslyUploadedViewHolder {
            val binding = ItemPreviouslyUploadedBinding.inflate(LayoutInflater.from(parent.context))
            return PreviouslyUploadedViewHolder(binding).apply { itemView.setOnClickListener {
                val activity = it.context.activity
                activity?.setResult(RESULT_OK, Intent().apply { data = Uri.parse(textView.text.toString()) })
                activity?.finish()
            }}
        }

        override fun onBindViewHolder(holder: PreviouslyUploadedViewHolder, position: Int) {
            val (drawable, path, lastModified) = getItem(position)
            holder.textView.text = path
            holder.textViewDateTime.text = lastModified
            holder.imageView.setImageDrawable(
                ResourcesCompat.getDrawable(holder.imageView.resources, drawable, holder.imageView.context.theme)
            )
        }
    }

    class PreviouslyUploadedViewHolder(binding: ItemPreviouslyUploadedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val imageView: ImageView = binding.imageViewItemPreviouslyUploaded
        val textView: TextView = binding.textViewItemPreviouslyUploaded
        val textViewDateTime: TextView = binding.textViewDateTimeItemPreviouslyUploaded
    }
}