/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ergflow.posenet

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.ergflow.posenet.databinding.TfePnActivityCameraBinding

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: TfePnActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TfePnActivityCameraBinding.inflate(layoutInflater)
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

    companion object {
        /**
         * Tag for the [Log].
         */
        private const val TAG = "CameraActivity"
    }
}
