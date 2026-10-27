package com.drivemount.app.di

import android.content.Context
import androidx.room.Room
import com.drivemount.app.data.db.AccountDao
import com.drivemount.app.data.db.DriveDatabase
import com.drivemount.app.data.db.DriveFileDao
import com.drivemount.app.data.db.UploadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database and DAO bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): DriveDatabase {
        return Room.databaseBuilder(
            context,
            DriveDatabase::class.java,
            DriveDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDriveFileDao(database: DriveDatabase): DriveFileDao {
        return database.driveFileDao()
    }

    @Provides
    fun provideAccountDao(database: DriveDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideUploadDao(database: DriveDatabase): UploadDao {
        return database.uploadDao()
    }
}

