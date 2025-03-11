/*
 * Updated: 2025-02-16 14:52:04
 * Author: William8677
 */

package com.williamfq.xhat.di

import android.content.Context
import com.williamfq.data.repository.StoryRepositoryImpl
import com.williamfq.domain.repository.StoryRepository
import com.williamfq.xhat.utils.analytics.Analytics
import com.williamfq.xhat.utils.metrics.StoryMetricsTracker
import com.williamfq.xhat.utils.metrics.StoryMetricsTrackerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StoryModule {
    @Binds
    @Singleton
    abstract fun bindStoryRepository(
        impl: StoryRepositoryImpl
    ): StoryRepository

    @Binds
    @Singleton
    abstract fun bindStoryMetricsTracker(
        impl: StoryMetricsTrackerImpl
    ): StoryMetricsTracker
}