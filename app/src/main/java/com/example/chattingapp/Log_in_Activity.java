package com.example.chattingapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class Log_in_Activity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnSignIn;
    private TextView tvSignUp, tvForgotPassword;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private DatabaseReference usersRef;

    private static final int RC_SIGN_IN = 1000;
    private static final int REQUEST_SIGNUP = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // Google Sign-In setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Button listeners
        btnSignIn.setOnClickListener(v -> loginUser());

        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(Log_in_Activity.this, SignUpActivity.class);
            startActivityForResult(intent, REQUEST_SIGNUP);
        });

        tvForgotPassword.setOnClickListener(v -> resetPassword());

        findViewById(R.id.btnGoogleSignIn).setOnClickListener(v -> signInWithGoogle());
    }

    // --------------------------
    // EMAIL & PASSWORD LOGIN
    // --------------------------
    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkAndSaveUser(mAuth.getCurrentUser());

                        Toast.makeText(Log_in_Activity.this, "Login successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Log_in_Activity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(Log_in_Activity.this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --------------------------
    // PASSWORD RESET
    // --------------------------
    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter your email first");
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(Log_in_Activity.this, "Password reset email sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(Log_in_Activity.this,
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --------------------------
    // GOOGLE SIGN-IN
    // --------------------------
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle SignUpActivity returning "Sign In"
        if (requestCode == REQUEST_SIGNUP && resultCode == RESULT_OK) {
            finish(); // Close original login activity to avoid multiple pages
            return;
        }

        // Google sign-in
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken(), account.getEmail());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        checkAndSaveUser(user);
                        Toast.makeText(this, "Google sign-in success", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Log_in_Activity.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --------------------------
    // SAVE USER TO DATABASE IF MISSING
    // --------------------------
    private void checkAndSaveUser(FirebaseUser user) {
        if (user == null) return;

        user.reload().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;

            String email = user.getEmail();
            if (email == null || email.isEmpty()) return;

            String safeEmail = email.replace(".", "_").replace("@", "_");
            String uid = user.getUid();

            usersRef.child(safeEmail).get().addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", email);
                    userData.put("uid", uid);
                    usersRef.child(safeEmail).updateChildren(userData);
                }
            });
        });
    }

    // --------------------------
    // AUTO LOGIN + AUTO SAVE IF USER NOT EXISTS
    // --------------------------
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkAndSaveUser(currentUser);
            startActivity(new Intent(Log_in_Activity.this, HomeActivity.class));
            finish();
        }
    }
}
