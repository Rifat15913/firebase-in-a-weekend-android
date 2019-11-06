/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.rifat.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String ANONYMOUS = "anonymous";
    public static final String KEY_FRIENDLY_MESSAGE_LENGTH = "friendly_message_length";
    // Constants
    private static final String TAG = "MainActivity";
    public static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;
    // UI components
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    // Variables
    private String mUsername;

    // Firebase core components
    private FirebaseDatabase mFirebaseDatabase;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseStorage mFirebaseStorage;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    // Firebase component references
    private DatabaseReference mMessagesDatabaseReference;
    private StorageReference mChatPhotosStorageReference;

    // Firebase listeners
    private ChildEventListener mChildEventListener;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();
        setListeners();
        loadData();
    }

    private void loadData() {
        // Fetch from firebase remote config to apply parameters
        mFirebaseRemoteConfig.fetchAndActivate()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        boolean updated = task.getResult();
                        Log.d(TAG, "Config params updated: " + updated);
                        applyLength();
                    } else {
                        applyLength();
                    }
                });
    }

    private void initialize() {
        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);
        mMessageListView = findViewById(R.id.messageListView);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Assign EditText text length
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)
        });

        // Initialize user name
        mUsername = ANONYMOUS;

        // Initialize firebase components
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // Initialize firebase component references
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        // Initialize firebase callbacks
        mAuthStateListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();

            if (user != null) {
                // User is signed in
                onSignedInInitialize(user.getDisplayName());
            } else {
                // User is signed out
                onSignedOutCleanUp();
                goForAuthUI();
            }
        };

        // Initialize firebase remote config and set parameters
        mFirebaseRemoteConfig.setConfigSettingsAsync(
                new FirebaseRemoteConfigSettings.Builder()
                        .build());

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(KEY_FRIENDLY_MESSAGE_LENGTH, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaultsAsync(defaultConfigMap);
    }

    private void setListeners() {
        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(view -> showImagePicker());

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mSendButton.setEnabled(charSequence.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(view -> sendMessage());
    }

    private void goForAuthUI() {
        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setIsSmartLockEnabled(false)
                        .setAvailableProviders(providers)
                        .build(),
                RC_SIGN_IN);
    }

    private void applyLength() {
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(
                (int) mFirebaseRemoteConfig.getLong(KEY_FRIENDLY_MESSAGE_LENGTH)
        )});
    }

    private void showImagePicker() {
        // Fire an intent to show an image picker
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Complete action using"),
                RC_PHOTO_PICKER);
    }

    private void sendMessage() {
        // Send messages on click
        mMessagesDatabaseReference.push().setValue(new FriendlyMessage(
                mMessageEditText.getText().toString().trim(),
                mUsername,
                null
        ));

        // Clear input box
        mMessageEditText.getText().clear();
    }

    private void onSignedInInitialize(String userName) {
        Toast.makeText(
                MainActivity.this,
                "You're now signed in. Welcome to Friendly Chat!",
                Toast.LENGTH_SHORT).show();

        mUsername = userName;
        attachReadDatabaseListener();
    }

    private void onSignedOutCleanUp() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void attachReadDatabaseListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(message);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };

            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.sign_out_menu) {
            // Sign out
            AuthUI.getInstance().signOut(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        onSignedOutCleanUp();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Signed in canceled!", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    uploadPhotoAndSendMessage(data);
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Photo picking is canceled!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void uploadPhotoAndSendMessage(@NotNull Intent data) {
        if (data.getData() != null
                && data.getData().getLastPathSegment() != null) {
            StorageReference photoReference =
                    mChatPhotosStorageReference.child(data.getData().getLastPathSegment());
            photoReference.putFile(data.getData())
                    .continueWithTask(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            throw task.getException();
                        }

                        return photoReference.getDownloadUrl();
                    }).addOnCompleteListener(this,
                    task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Uri downloadUrl = task.getResult();
                            FriendlyMessage message = new FriendlyMessage(null,
                                    mUsername,
                                    downloadUrl.toString()
                            );

                            mMessagesDatabaseReference.push().setValue(message);
                        } else {
                            Toast.makeText(this, "Photo could not be uploaded",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        }
    }
}
