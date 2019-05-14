package com.lazylantern.midgar.sample

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import com.lazylantern.midgar.R

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
