package dev.healthtracker.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.healthtracker.app.data.auth.SupabaseConfig
import dev.healthtracker.app.data.local.AppDatabase
import dev.healthtracker.app.data.local.DatabaseKeyProvider
import dev.healthtracker.app.data.local.LogDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = DatabaseKeyProvider(context).getOrCreatePassphrase()
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()

    @Provides
    @Singleton
    fun provideSupabase(): SupabaseClient = createSupabaseClient(
        supabaseUrl = if (SupabaseConfig.isConfigured) SupabaseConfig.url else SupabaseConfig.PLACEHOLDER_URL,
        supabaseKey = SupabaseConfig.publishableKey.ifBlank { "not-configured" },
    ) {
        install(Auth)
        install(Postgrest)
    }
}
