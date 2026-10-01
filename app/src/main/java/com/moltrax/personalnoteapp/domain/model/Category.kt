package com.moltrax.personalnoteapp.domain.model

/**
 * Görev kategorisi. [name] aynı zamanda birincil anahtardır (görevler kategoriyi ada göre
 * referanslar — bkz. [Task.category]).
 *
 * - [isPermanent] = true: kullanıcı tarafından kalıcı tanımlanmış kategori. İçinde görev olmasa
 *   bile filtre menüsünde her zaman görünür ve otomatik temizliğe takılmaz.
 * - [isPermanent] = false: geçici kategori. Bir göreve kategori yazıldığında otomatik oluşur;
 *   bağlı görevi kalmadığında otomatik silinir.
 */
data class Category(
    val name: String,
    val isPermanent: Boolean = false,
    // Mezar taşı: silinen kategori kaydı saklanır ki Drive senkronizasyonu silinmiş
    // kategoriyi uzaktan geri diriltmesin. Görünür listeler DAO katmanında filtrelenir.
    val isDeleted: Boolean = false,
)
