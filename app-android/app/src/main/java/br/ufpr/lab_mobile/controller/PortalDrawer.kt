package br.ufpr.lab_mobile.controller

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.TextView
import br.ufpr.lab_mobile.service.SessionStore
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
        activity.watchSessionExpiry()
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
            activity.returnToLogin(clear = true)
        }
        updateMenu(false)
    }

    fun onResume() {
        if (activity.requireSessionToken() == null) return
        val session = SessionStore.get(activity).current ?: return
        activity.findViewById<TextView>(R.id.quotes_greeting)?.text = activity.getString(
            R.string.portal_greeting, session.user.name?.trim()?.takeIf(String::isNotEmpty) ?: activity.getString(R.string.portal_client),
        )
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
        if ((destination == selected && activity.javaClass == destination.screen) || navigating) return
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
