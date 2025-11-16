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
import com.google.firebase.database.*;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        // Firebase setup
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Initialize UI
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvSignUp = findViewById(R.id.tvSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // Google Sign-In options
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Listeners
        btnSignIn.setOnClickListener(v -> loginUser());
        tvSignUp.setOnClickListener(v -> startActivity(new Intent(Log_in_Activity.this, SignUpActivity.class)));
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
                        Toast.makeText(Log_in_Activity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(Log_in_Activity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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
        if (user == null || user.getEmail() == null) return;

        String email = user.getEmail();
        String safeEmail = email.replace(".", "_").replace("@", "_");
        String uid = user.getUid();

        DatabaseReference userRef = usersRef.child(safeEmail);

        userRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                // Save minimal info needed for chats
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", email);
                userData.put("uid", uid);

                userRef.updateChildren(userData)
                        .addOnSuccessListener(aVoid -> {
                            // Optional: log or toast for debug
                            // Toast.makeText(this, "User saved in Firebase", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error saving user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }
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
            // ensure user exists in Firebase
            checkAndSaveUser(currentUser);

            startActivity(new Intent(Log_in_Activity.this, HomeActivity.class));
            finish();
        }
    }

    @Override
    public void finish(){
        super.finish();
        if (mAuth.getCurrentUser() != null)
            checkAndSaveUser(mAuth.getCurrentUser());
    }
}
