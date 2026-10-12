package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.drawerlayout.widget.DrawerLayout
import br.ufpr.lab_mobile.R

internal enum class PortalDestination(val itemId: Int, val screen: Class<out AppCompatActivity>) {
    QUOTES(R.id.navQuotes, QuotesActivity::class.java),
    REPORTS(R.id.navReports, ResultsActivity::class.java),
    EQUIPMENT(R.id.navEquipment, EquipmentActivity::class.java),
}

internal class PortalDrawer(
    private val activity: AppCompatActivity,
    private val token: String,
    private val selected: PortalDestination,
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
        drawer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resizePanel() }
        menu.setOnClickListener {
            if (drawer.isDrawerVisible(Gravity.RIGHT)) drawer.closeDrawer(Gravity.RIGHT)
            // Finish responsive sizing before the animation calculates the drawer's open position.
            else {
                resizePanel()
                panel.doOnLayout { drawer.openDrawer(Gravity.RIGHT) }
            }
        }
        PortalDestination.entries.forEach { destination ->
            activity.findViewById<View>(destination.itemId).apply {
                isSelected = destination == selected
                setOnClickListener { open(destination) }
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

    private fun resizePanel() {
        val width = minOf(
            (200 * activity.resources.displayMetrics.density * activity.resources.configuration.fontScale).toInt(),
            drawer.width * 2 / 3,
        )
        if (width > 0 && (panel.layoutParams.width != width || panel.width != width)) {
            panel.layoutParams = panel.layoutParams.apply { this.width = width }
        }
    }

    private fun open(destination: PortalDestination) {
        drawer.closeDrawer(Gravity.RIGHT)
        if (destination == selected || navigating) return
        navigating = true
        activity.startActivity(Intent(activity, destination.screen)
            .putExtra(LoginActivity.EXTRA_ACCESS_TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        if (selected != PortalDestination.QUOTES) activity.finish()
    }

    private fun updateMenu(open: Boolean) {
        back.isEnabled = open
        menu.setImageResource(if (open) R.drawable.ic_menu_close else R.drawable.ic_menu)
        menu.contentDescription = activity.getString(if (open) R.string.menu_close else R.string.menu_open)
    }
}
