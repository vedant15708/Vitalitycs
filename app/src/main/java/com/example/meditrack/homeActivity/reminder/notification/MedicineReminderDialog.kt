package com.example.meditrack.homeActivity.reminder.notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.LayoutRes
import com.google.firebase.auth.FirebaseAuth
import androidx.fragment.app.DialogFragment
import com.example.meditrack.R
import com.example.meditrack.homeActivity.reminder.recevier.ReminderReceiver
import com.example.meditrack.utility.UtilsFunctions.Companion.TIME_FORMAT
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.properties.Delegates

class MedicineReminderDialog(val reqActivity: Activity): DialogFragment() {
    private lateinit var dialog: View
    //    private lateinit var timePickerDialog: TimePickerDialog
    private var notfiHour by Delegates.notNull<Int>()
    private var notfiMin by Delegates.notNull<Int>()
    private lateinit var reminderTimeEditText: TextView
    private val selectedDays = mutableListOf<Int>()
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firestore
        firestore = Firebase.firestore
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                // However, don't call retrieveMedicinesFromFirestore() here
            } else {
                // No user is signed in, handle the case accordingly
                Log.e("Firestore", "No user is signed in")
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = reqActivity.layoutInflater
        dialog = inflater.inflate(R.layout.addmedicine_reminder_dialogbox_layout, null)

        val medicineSpinner = dialog.findViewById<Spinner>(R.id.medicineSpinner)
        val addMediReminderTime: Button = dialog.findViewById(R.id.add_medicine_timePicker_btn)

        // Check if the user is signed in before retrieving medicines
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            retrieveMedicinesFromFirestore(medicineSpinner)
        }
        // Open the Time Picker, when click on the button
        addMediReminderTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(
                reqActivity,
                { _, hourOfDay, minute ->
                    setTime(hourOfDay, minute)
                    notfiHour = hourOfDay
                    notfiMin = minute
                },
                currentHour,
                currentMinute,
                false
            )
            timePickerDialog.show()
        }

        // Notification according Days
        val dayCheckBoxes = listOf(
            dialog.findViewById<CheckBox>(R.id.checkSunday),
            dialog.findViewById(R.id.checkMonday),
            dialog.findViewById(R.id.checkTuesday),
            dialog.findViewById(R.id.checkWednesday),
            dialog.findViewById(R.id.checkThursday),
            dialog.findViewById(R.id.checkFriday),
            dialog.findViewById(R.id.checkSaturday)
        )

        // Handle checkbox selection
        for (i in dayCheckBoxes.indices) {
            dayCheckBoxes[i].setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedDays.add(i)
                    Log.i("TAG", "onCreateDialog: selectedDays = $selectedDays")
                } else {
                    selectedDays.remove(i)
                }
            }
            Log.i("TAG", "onCreateDialog: selectedDays = $selectedDays")
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(reqActivity)
        builder.setView(dialog)
            .setTitle("Medicine Reminder")
            .setPositiveButton("Set Reminder") { dialog, which ->
                val selectedMedicineName = medicineSpinner.selectedItem.toString()
                Log.i("TAG", "positiveBtn: $selectedDays")
                // Schedule the notification
                scheduleNotifications(selectedMedicineName, selectedDays, notfiHour, notfiMin)
                // Dismiss the dialog
                dialog.dismiss()
                Toast.makeText(reqActivity, "Reminder Successfully set", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, which ->
                // Handle the "Cancel" button click
                dialog.dismiss()
                Toast.makeText(reqActivity, "Reminder Canceled", Toast.LENGTH_SHORT).show()
            }

        createNotificationChannel()

        return builder.create()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "1",
                "Medicine Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = reqActivity?.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun setTime(Hour: Int, Min: Int){
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, Hour)
        calendar.set(Calendar.MINUTE, Min)
        val sdf = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()) // Format for 12-hour time with AM/PM
        val formattedTime = sdf.format(calendar.time)
    }

    fun scheduleNotifications(
        medicineName: String,
        days: MutableList<Int> = mutableListOf(),
        notfiHr: Int,
        notfiMin: Int
    ) {
        val now = Calendar.getInstance()
        Log.i("TAG", "scheduleNotifications: days = $days")
        Log.i("TAG", "scheduleNotifications: hour =  $notfiHr")
        Log.i("TAG", "scheduleNotifications: min = $notfiMin")
        for (day in days) {
            // Calculate the next occurrence of the selected day
            val nextOccurrence = calculateNextOccurrence(day, now, notfiHr, notfiMin)
            scheduleNotification(
                "Reminder to take $medicineName",
                nextOccurrence.get(Calendar.DAY_OF_WEEK),
                notfiHr,
                notfiMin
            )
        }
    }

    private fun calculateNextOccurrence(selectedDay: Int, now: Calendar, hour: Int, minute: Int): Calendar {
        val nextOccurrence = now.clone() as Calendar
        nextOccurrence.set(Calendar.HOUR_OF_DAY, hour)
        nextOccurrence.set(Calendar.MINUTE, minute)
        nextOccurrence.set(Calendar.SECOND, 0)

        val today = nextOccurrence.get(Calendar.DAY_OF_WEEK)
        if (today > selectedDay || (today == selectedDay && nextOccurrence.before(now))) {
            // If the selected day is in the past relative to the current day or the same day with a past time,
            // add 7 days to schedule the notification for the upcoming day with the same name.
            nextOccurrence.add(Calendar.DAY_OF_MONTH, 7)
        } else {
            // Calculate the days to add to reach the selected day in the future.
            val daysToAdd = selectedDay - today
            nextOccurrence.add(Calendar.DAY_OF_MONTH, daysToAdd)
        }

        return nextOccurrence
    }
//    private fun retrieveMedicinesFromFirestore(medicineSpinner: Spinner) {
//        val currentUser = FirebaseAuth.getInstance().currentUser
//        if (currentUser != null) {
//            val userDocRef = firestore.collection("users_data").document(currentUser.uid)
//
//            // Query the "medicine_data" subcollection within the user's document
//            userDocRef.collection("medicine_data")
//                .get()
//                .addOnSuccessListener { querySnapshot ->
//                    val medicineList = mutableListOf<String>()
//                    for (document in querySnapshot) {
//                        val medicineName = document.getString("medname") ?: ""
//                        medicineList.add(medicineName)
//                    }
//                    populateSpinner(medicineList, medicineSpinner)
//                }
//                .addOnFailureListener { exception ->
//                    Log.e("Firestore", "Error retrieving medicines: $exception")
//                    // Handle error
//                }
//        } else {
//            // No user is signed in, handle the case accordingly
//            Log.e("Firestore", "No user is signed in")
//        }
//    }




    private fun retrieveMedicinesFromFirestore(medicineSpinner: Spinner) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userDocRef = firestore.collection("users_data").document(currentUser.uid)

            // Query the "medicine_data" subcollection within the user's document
            userDocRef.collection("medicine_data")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val medicineList = mutableListOf<String>()
                    for (document in querySnapshot) {
                        val medicineName = document.getString("medName")
                        if (!medicineName.isNullOrEmpty()) {
                            medicineList.add(medicineName)
                        }
                    }
                    populateSpinner(medicineList, medicineSpinner)
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error retrieving medicines: $exception")
                    // Handle error
                }
        } else {
            // No user is signed in, handle the case accordingly
            Log.e("Firestore", "No user is signed in")
        }
    }

    private fun populateSpinner(medicineList: List<String>, medicineSpinner: Spinner) {
        val adapter = MedicineSpinnerAdapter(requireContext(), android.R.layout.simple_spinner_item, medicineList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        medicineSpinner.adapter = adapter
    }

//   private class MedicineSpinnerAdapter(
//        context: Context,
//        @LayoutRes private val layoutResource: Int,
//        private val medicineList: List<String>
//    ) : ArrayAdapter<String>(context, layoutResource, medicineList) {
//
//        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
//            val view = super.getView(position, convertView, parent)
//            val textView = view.findViewById<TextView>(android.R.id.text1)
//            textView.text = medicineList[position]
//            return view
//        }
//
//        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
//            val view = super.getDropDownView(position, convertView, parent)
//            val textView = view.findViewById<TextView>(android.R.id.text1)
//            textView.text = medicineList[position]
//            return view
//        }
//    }

    private class MedicineSpinnerAdapter(
        context: Context,
        @LayoutRes private val layoutResource: Int,
        private val medicineList: List<String>
    ) : ArrayAdapter<String>(context, layoutResource, medicineList) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.setTextColor(Color.BLACK) // Set a contrasting color
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Set a visible text size
            view.text = medicineList[position]
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent) as TextView
            view.setTextColor(Color.BLACK) // Set a contrasting color
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) // Set a visible text size
            view.text = medicineList[position]
            return view
        }
    }





    private fun scheduleNotification(content: String, dayOfWeek: Int, hour: Int, minute: Int) {
        // Create an Intent for the ReminderReceiver
        val intent = Intent(reqActivity, ReminderReceiver::class.java)
        intent.putExtra("title", "Medicine Reminder")
        intent.putExtra("content", content)

        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                reqActivity,
                Random().nextInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getBroadcast(
                reqActivity,
                Random().nextInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // Get an instance of the AlarmManager
        val alarmManager = reqActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calculate the time in milliseconds
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        val alarmTime = calendar.timeInMillis

        // Set the notification to appear at the specified time
        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent)
    }
    override fun onDestroy() {
        super.onDestroy()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

}
