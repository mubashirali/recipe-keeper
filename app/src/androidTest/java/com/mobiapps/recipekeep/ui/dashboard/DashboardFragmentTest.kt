package com.mobiapps.recipekeep.ui.dashboard

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiapps.recipekeep.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardFragmentTest {

    @Test
    fun dashboardFragment_recyclerViewAndFab_areDisplayed() {
        launchFragmentInContainer<DashboardFragment>(
            themeResId = R.style.Theme_RecipeKeeper
        )
        onView(withId(R.id.recycler_recipes)).check(matches(isDisplayed()))
        onView(withId(R.id.fab_add_recipe)).check(matches(isDisplayed()))
    }
}
