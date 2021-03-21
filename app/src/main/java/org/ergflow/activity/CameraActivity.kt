
package org.ergflow.activity

import android.os.Bundle
import android.view.Menu
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.ergflow.activity.databinding.EfActivityCameraBinding

/***
 * CameraActivity is the parent activity. It contains the toolbar and PosenetActivity.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: EfActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EfActivityCameraBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setSupportActionBar(binding.toolbar)
        val posenetActivity = PosenetActivity()
        savedInstanceState ?: supportFragmentManager.beginTransaction()
            .replace(R.id.container, posenetActivity).commit()
        binding.toolbar.setOnMenuItemClickListener { item ->
            posenetActivity.onToolbarItem(item)
            onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }
}
