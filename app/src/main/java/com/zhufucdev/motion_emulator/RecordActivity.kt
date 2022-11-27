package com.zhufucdev.motion_emulator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.google.android.material.snackbar.Snackbar
import com.zhufucdev.motion_emulator.data.Cells
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.MotionRecorder
import com.zhufucdev.motion_emulator.data.TelephonyRecorder
import com.zhufucdev.motion_emulator.databinding.ActivityRecordBinding

class RecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        MotionRecorder.init(this)
        TelephonyRecorder.init(this)
        Motions.require(this)
        Cells.require(this)
        super.onCreate(savedInstanceState)

        binding = ActivityRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController = findNavController(R.id.nav_host_fragment_activity_record)
        initializeToolbar(binding.appBarToolbar, navController)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
                ),
                0
            )
        }
    }

    private fun requirePermissions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Snackbar
                .make(binding.root, R.string.text_permission_not_granted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_grant) { requirePermissions() }
                .show()
        }
    }
}