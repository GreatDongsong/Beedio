/*
 * Beedio is an Android app for downloading videos
 * Copyright (C) 2019 Loremar Marabillas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.beedio

import dagger.Module
import dagger.android.ContributesAndroidInjector
import marabillas.loremar.beedio.base.di.ActivityScope
import marabillas.loremar.beedio.browser.activity.BrowserActivity
import marabillas.loremar.beedio.download.DownloadActivity
import marabillas.loremar.beedio.home.HomeActivity

@Module
abstract class ActivityBindingModule {
    @ActivityScope
    @ContributesAndroidInjector(modules = [HomeActivityModule::class])
    abstract fun contributeHomeActivity(): HomeActivity

    @ActivityScope
    @ContributesAndroidInjector(modules = [BrowserActivityModule::class])
    abstract fun contributeBrowserActivity(): BrowserActivity

    @ActivityScope
    @ContributesAndroidInjector
    abstract fun contributeDownloadActivity(): DownloadActivity
}