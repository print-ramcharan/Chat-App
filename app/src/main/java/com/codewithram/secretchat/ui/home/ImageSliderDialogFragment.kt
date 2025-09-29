import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.codewithram.secretchat.R

class ImageSliderDialogFragment(
    private val images: List<Bitmap>,
    private val startIndex: Int
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_slider)

        val viewPager = dialog.findViewById<ViewPager2>(R.id.imagePager)
        viewPager.adapter = ImageSliderAdapter(images)
        viewPager.setCurrentItem(startIndex, false)

        return dialog
    }

    private class ImageSliderAdapter(private val images: List<Bitmap>) :
        RecyclerView.Adapter<ImageSliderAdapter.ImageViewHolder>() {

        class ImageViewHolder(private val imageView: ImageView) :
            RecyclerView.ViewHolder(imageView) {
            fun bind(bitmap: Bitmap) {
                imageView.setImageBitmap(bitmap)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.BLACK)
            }
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(images[position])
        }

        override fun getItemCount() = images.size
    }
}
