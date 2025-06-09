
package com.mobileconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    private TextView yourIdTextView;
    private EditText enterIdEditText;
    private Button startButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        yourIdTextView = findViewById(R.id.textYourId);
        enterIdEditText = findViewById(R.id.editEnterId);
        startButton = findViewById(R.id.buttonStart);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            yourIdTextView.setText("Your ID: " + currentUser.getUid());
        } else {
            Toast.makeText(this, "No user signed in", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }

        startButton.setOnClickListener(v -> {
            String enteredId = enterIdEditText.getText().toString().trim();
            if (!enteredId.isEmpty()) {
                // Handle connection logic later
                Toast.makeText(this, "Trying to connect to ID: " + enteredId, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enter an ID", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_profile) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                String name = user.getDisplayName();
                String email = user.getEmail();
                Toast.makeText(this, "Name: " + name + "
Email: " + email, Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.menu_logout) {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
