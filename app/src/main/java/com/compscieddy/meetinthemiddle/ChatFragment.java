package com.compscieddy.meetinthemiddle;

import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Created by ambar on 6/12/16.
 */
public class ChatFragment extends Fragment {

  private FirebaseAuth mAuth;
  private DatabaseReference mRef;
  private ImageView mSendButton;
  private EditText mMessageEdit;

  private RecyclerView mMessages;
  private LinearLayoutManager mManager;
  private FirebaseRecyclerAdapter<Chat, ChatHolder> mRecyclerViewAdapter;


  public static ChatFragment newInstance() {

    Bundle args = new Bundle();
    //For future arguments, add here
    ChatFragment fragment = new ChatFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_chat, container, false);
    mAuth = FirebaseAuth.getInstance();
    mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
      @Override
      public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        updateUI();
      }
    });

    mSendButton = (ImageView) view.findViewById(R.id.message_send_button);
    mMessageEdit = (EditText) view.findViewById(R.id.message_edit_text);

    mRef = FirebaseDatabase.getInstance().getReference();

    mSendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String uid = mAuth.getCurrentUser().getUid();
        String name = "User " + uid.substring(0, 6);

        Chat chat = new Chat(name, uid, mMessageEdit.getText().toString());
        mRef.push().setValue(chat, new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError databaseError, DatabaseReference reference) {
            if (databaseError != null) {
              Log.e("Failed to write message", databaseError.toException().toString());
            }
          }
        });

        mMessageEdit.setText("");
      }
    });

    mMessages = (RecyclerView) view.findViewById(R.id.chats_recycler_view);

    mManager = new LinearLayoutManager(getActivity());
    mManager.setReverseLayout(false);

    mMessages.setHasFixedSize(false);
    mMessages.setLayoutManager(mManager);

    return view;
  }

  @Override
  public void onStart() {
    super.onStart();
    // Default Database rules do not allow unauthenticated reads, so we need to
    // sign in before attaching the RecyclerView adapter otherwise the Adapter will
    // not be able to read any data from the Database.
    if (!isSignedIn()) {
      signInAnonymously();
    } else {
      attachRecyclerViewAdapter();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (mRecyclerViewAdapter != null) {
      mRecyclerViewAdapter.cleanup();
    }
  }

  private void attachRecyclerViewAdapter() {
    mRecyclerViewAdapter = new FirebaseRecyclerAdapter<Chat, ChatHolder>(
        Chat.class, R.layout.item_chat, ChatHolder.class, mRef) {

      @Override
      public void populateViewHolder(ChatHolder chatView, Chat chat, int position) {
        chatView.setName(chat.getName());
        chatView.setText(chat.getText());

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && chat.getUid().equals(currentUser.getUid())) {
          chatView.setIsSender(true);
        } else {
          chatView.setIsSender(false);
        }
      }
    };

    // Scroll to bottom on new messages
    mRecyclerViewAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        mManager.smoothScrollToPosition(mMessages, null, mRecyclerViewAdapter.getItemCount());
      }
    });

    mMessages.setAdapter(mRecyclerViewAdapter);
  }

  private void signInAnonymously() {
    Toast.makeText(getContext(), "Signing in...", Toast.LENGTH_SHORT).show();
    mAuth.signInAnonymously()
        .addOnCompleteListener(getActivity(), new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            if (task.isSuccessful()) {
              Toast.makeText(getActivity(), "Signed In",
                  Toast.LENGTH_SHORT).show();
              attachRecyclerViewAdapter();
            } else {
              Toast.makeText(getActivity(), "Sign In Failed",
                  Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  public boolean isSignedIn() {
    return (mAuth.getCurrentUser() != null);
  }

  public void updateUI() {
    // Sending only allowed when signed in
    mSendButton.setEnabled(isSignedIn());
    mMessageEdit.setEnabled(isSignedIn());
  }

  public static class Chat {

    String name;
    String text;
    String uid;

    public Chat() {
    }

    public Chat(String name, String uid, String message) {
      this.name = name;
      this.text = message;
      this.uid = uid;
    }

    public String getName() {
      return name;
    }

    public String getUid() {
      return uid;
    }

    public String getText() {
      return text;
    }
  }


  public static class ChatHolder extends RecyclerView.ViewHolder {
    View mView;

    public ChatHolder(View itemView) {
      super(itemView);
      mView = itemView;
    }

    public void setIsSender(Boolean isSender) {
      FrameLayout left_arrow = (FrameLayout) mView.findViewById(R.id.left_arrow);
      FrameLayout right_arrow = (FrameLayout) mView.findViewById(R.id.right_arrow);
      RelativeLayout messageContainer = (RelativeLayout) mView.findViewById(R.id.message_container);
      LinearLayout message = (LinearLayout) mView.findViewById(R.id.message);

      int color;
      if (isSender) {
        color = ContextCompat.getColor(mView.getContext(), R.color.group_chat_background_color);
        left_arrow.setVisibility(View.GONE);
        right_arrow.setVisibility(View.VISIBLE);
        messageContainer.setGravity(Gravity.RIGHT);
      } else {
        color = ContextCompat.getColor(mView.getContext(), R.color.user_chat_background_color);
        left_arrow.setVisibility(View.VISIBLE);
        right_arrow.setVisibility(View.GONE);
        messageContainer.setGravity(Gravity.LEFT);
      }

      ((GradientDrawable) message.getBackground()).setColor(color);
      ((RotateDrawable) left_arrow.getBackground()).getDrawable()
          .setColorFilter(color, PorterDuff.Mode.SRC);
      ((RotateDrawable) right_arrow.getBackground()).getDrawable()
          .setColorFilter(color, PorterDuff.Mode.SRC);
    }

    public void setName(String name) {
      TextView field = (TextView) mView.findViewById(R.id.name_text);
      field.setText(name);
    }

    public void setText(String text) {
      TextView field = (TextView) mView.findViewById(R.id.message_text);
      field.setText(text);
    }
  }


}
