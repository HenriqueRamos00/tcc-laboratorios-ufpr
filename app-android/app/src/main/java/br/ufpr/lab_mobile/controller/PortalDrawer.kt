package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import br.ufpr.lab_mobile.R

/** Navigation shared by the quotes list and the standalone results demonstration. */
internal class PortalDrawer(
    private val activity: AppCompatActivity,
    private val token: String,
    private val reports: Boolean,
) {
    private val drawer = activity.findViewById<DrawerLayout>(R.id.portalDrawer)
    private val panel = activity.findViewById<View>(R.id.drawerPanel)
    private val menu = activity.findViewById<ImageButton>(R.id.menuButton)
    private var navigating = false
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = drawer.closeDrawer(Gravity.RIGHT)
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, back)
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = updateMenu(slideOffset > 0)
            override fun onDrawerOpened(drawerView: View) = updateMenu(true)
            override fun onDrawerClosed(drawerView: View) = updateMenu(false)
        })
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = minOf(
                (200 * activity.resources.displayMetrics.density * activity.resources.configuration.fontScale).toInt(),
                drawer.width * 2 / 3,
            )
            if (width > 0 && panel.layoutParams.width != width) {
                panel.layoutParams = panel.layoutParams.apply { this.width = width }
            }
        }
        menu.setOnClickListener {
            if (drawer.isDrawerVisible(Gravity.RIGHT)) drawer.closeDrawer(Gravity.RIGHT)
            else drawer.openDrawer(Gravity.RIGHT)
        }
        activity.findViewById<View>(if (reports) R.id.navReports else R.id.navQuotes).isSelected = true
        activity.findViewById<View>(R.id.navHome).setOnClickListener { openQuotes() }
        activity.findViewById<View>(R.id.navQuotes).setOnClickListener { openQuotes() }
        activity.findViewById<View>(R.id.navReports).setOnClickListener {
            drawer.closeDrawer(Gravity.RIGHT)
            if (!reports && !navigating) {
                navigating = true
                activity.startActivity(Intent(activity, ResultsActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, token)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        listOf(R.id.navEquipment, R.id.navProfile).forEach { id ->
            activity.findViewById<View>(id).setOnClickListener {
                drawer.closeDrawer(Gravity.RIGHT)
                Toast.makeText(activity, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }
        activity.findViewById<View>(R.id.logoutButton).setOnClickListener {
            LoginActivity.clearSession(activity)
            activity.startActivity(Intent(activity, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            activity.finish()
        }
        updateMenu(false)
    }

    fun onResume() {
        navigating = false
        updateMenu(drawer.isDrawerVisible(Gravity.RIGHT))
    }

    private fun openQuotes() {
        drawer.closeDrawer(Gravity.RIGHT)
        if (reports && !navigating) {
            navigating = true
            activity.startActivity(Intent(activity, QuotesActivity::class.java)
                .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            activity.finish()
        }
    }

    private fun updateMenu(open: Boolean) {
        back.isEnabled = open
        menu.setImageResource(if (open) R.drawable.ic_menu_close else R.drawable.ic_menu)
        menu.contentDescription = activity.getString(if (open) R.string.menu_close else R.string.menu_open)
    }
}
