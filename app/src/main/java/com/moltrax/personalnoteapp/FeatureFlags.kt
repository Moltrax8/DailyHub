package com.moltrax.personalnoteapp

/**
 * Geçici özellik bayrakları.
 *
 * [DRIVE_SYNC_ENABLED] şimdilik KAPALI: release APK'nın imza SHA-1'i Google Cloud
 * Console'da kayıtlı olmadığından (OAuth istemcisi doğrulanmadığından) Google ile
 * giriş başarısız oluyor ve uygulamaya hiç girilemiyordu. Bu bayrak kapalıyken:
 *  - uygulama giriş ekranını atlayıp doğrudan ana ekrandan açılır,
 *  - Google Drive senkronizasyonu ve ayarlardaki hesap/yedekleme bölümü gizlenir.
 *
 * İlgili kod (LoginScreen, AuthViewModel, Drive servisleri, SyncRepository, SyncWorker)
 * yerinde durur; SHA-1 kaydı yapıldıktan sonra bayrağı `true` yapmak yeniden etkinleştirir.
 */
object FeatureFlags {
    const val DRIVE_SYNC_ENABLED = false
}
