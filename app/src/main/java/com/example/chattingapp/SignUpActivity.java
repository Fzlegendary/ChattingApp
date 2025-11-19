package com.example.chattingapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SignUpActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etEmail, etPassword, etConfirmPassword;
    private FirebaseAuth auth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        etFullName = findViewById(R.id.etFullName2);
        etEmail = findViewById(R.id.etSignUpEmail2);
        etPassword = findViewById(R.id.etSignUpPassword2);
        etConfirmPassword = findViewById(R.id.etConfirmPassword2);
        Button btnSignUp = findViewById(R.id.btnSignUp2);
        TextView tvSignIn = findViewById(R.id.tvSignIn2);

        btnSignUp.setOnClickListener(v -> registerUser());

        tvSignIn.setOnClickListener(v -> {
            // Return RESULT_OK to Log_in_Activity
            setResult(RESULT_OK);
            new android.os.Handler().postDelayed(() -> {
                // Code to execute after 0.7 seconds
                continueAfterDelay();
            }, 700);
        });
    }
    private void continueAfterDelay() {
        // This code runs after 0.7 seconds
       Intent intent = new Intent(this, Log_in_Activity.class);
       startActivity(intent);
       finish();
    }

    private void registerUser() {
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Enter your name");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter password");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user == null) {
                            Toast.makeText(this, "Error: User is null", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Update Firebase Auth display name
                        UserProfileChangeRequest profileUpdates =
                                new UserProfileChangeRequest.Builder()
                                        .setDisplayName(fullName)
                                        .build();
                        user.updateProfile(profileUpdates);

                        // Save user data into Realtime Database
                        String safeEmail = email.replace(".", "_");
                        String passwordHash = sha256(password);

                        usersRef.child(safeEmail).child("email").setValue(email);
                        usersRef.child(safeEmail).child("uid").setValue(user.getUid());
                        usersRef.child(safeEmail).child("name").setValue(fullName);
                        usersRef.child(safeEmail).child("passwordHash").setValue(passwordHash);

                        Toast.makeText(SignUpActivity.this, "Account created!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignUpActivity.this, Log_in_Activity.class));
                        finish();
                    } else {
                        Toast.makeText(SignUpActivity.this,
                                "Sign-up failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
