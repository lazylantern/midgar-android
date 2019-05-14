package com.lazylantern.midgar.sample

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity;
import com.lazylantern.midgar.R

import kotlinx.android.synthetic.main.activity_sample.*

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val intent = Intent(this, SubActivity::class.java)
            startActivity(intent)
        }
    }

}
