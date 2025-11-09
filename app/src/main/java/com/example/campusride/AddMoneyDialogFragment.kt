package com.example.campusride

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddMoneyDialogFragment : DialogFragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val builder = MaterialAlertDialogBuilder(activity)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_add_money, null)

            val etAmount = view.findViewById<EditText>(R.id.etAmount)

            builder.setView(view)
                .setTitle("Add Money")
                .setPositiveButton("Add") { dialog, _ ->
                    val amountText = etAmount.text.toString()
                    if (amountText.isNotEmpty()) {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            addMoneyToWallet(amount)
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun addMoneyToWallet(amount: Double) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("passengers").document(uid)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentBalance = snapshot.getDouble("walletBalance") ?: 0.0
            val newBalance = currentBalance + amount
            transaction.update(userRef, "walletBalance", newBalance)

            // You might also want to add a transaction record here
        }.addOnSuccessListener {
            // Notify the parent activity to refresh the balance
            (activity as? WalletActivity)?.loadBalance()
        }.addOnFailureListener { e ->
            // Handle error
        }
    }
}