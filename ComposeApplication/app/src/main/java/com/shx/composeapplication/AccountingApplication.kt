package com.shx.composeapplication

import android.app.Application
import com.shx.composeapplication.data.database.AppDatabase
import com.shx.composeapplication.data.repository.AccountRepository

class AccountingApplication : Application() {

    val accountRepository: AccountRepository by lazy {
        val db = AppDatabase.getInstance(this)
        AccountRepository(db.accountRecordDao())
    }
}
