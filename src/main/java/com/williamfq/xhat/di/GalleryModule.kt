/*
 * Updated: 2025-01-21 20:39:04
 * Author: William8677
 */

package com.williamfq.xhat.di

import android.content.Context
import com.williamfq.xhat.domain.repository.GalleryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GalleryModule {

    @Provides
    @Singleton
    fun provideGalleryRepository(
        @ApplicationContext context: Context
    ): GalleryRepository = GalleryRepository(context)
}