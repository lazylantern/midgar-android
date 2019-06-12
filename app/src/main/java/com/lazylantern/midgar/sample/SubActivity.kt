package com.lazylantern.midgar.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import kotlinx.android.synthetic.main.activity_sub.*

class SubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            supportFragmentManager.beginTransaction().add(R.id.fragment_host, BlankFragment.newInstance(), "Blank Fragment").commit()
        }

        supportFragmentManager.beginTransaction().add(BlankFragment.newInstance(), "Blank Fragment not attached to the view").commit()
    }

}
