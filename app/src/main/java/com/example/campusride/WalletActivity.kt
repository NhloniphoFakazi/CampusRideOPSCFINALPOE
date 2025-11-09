package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WalletActivity : AppCompatActivity() {

    private lateinit var tvBalance: TextView
    private lateinit var btnAddMoney: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "WalletActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)

        tvBalance = findViewById(R.id.tvBalance)
        btnAddMoney = findViewById(R.id.btnAddMoney)

        btnAddMoney.setOnClickListener {
            // Show the AddMoneyDialogFragment
            val dialog = AddMoneyDialogFragment()
            dialog.show(supportFragmentManager, "AddMoneyDialog")
        }
    }

    override fun onResume() {
        super.onResume()
        loadBalance()
    }

    fun loadBalance() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("passengers").document(uid).get()
            .addOnSuccessListener { doc ->
                val bal = doc.getDouble("walletBalance") ?: 0.0
                tvBalance.text = "Balance: ${"%.2f".format(bal)}"
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to load wallet balance: ${e.message}")
                tvBalance.text = "Balance: ?"
            }
    }
}