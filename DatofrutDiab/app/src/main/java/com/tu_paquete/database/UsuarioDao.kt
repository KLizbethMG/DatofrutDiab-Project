package com.example.datofrutdiab.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsuarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarUsuario(usuario: Usuario)

    @Query("SELECT * FROM usuarios LIMIT 1")
    suspend fun obtenerUsuario(): Usuario?
}