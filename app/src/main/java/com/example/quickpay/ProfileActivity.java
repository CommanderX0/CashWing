package com.example.quickpay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import java.util.Calendar;

public class ProfileActivity extends AppCompatActivity {
    private static final String SUPPORT_PHONE = "01011475826";
    private final CloudWalletRepository cloud = new CloudWalletRepository();
    private String phone;
    private TextView textName,textPhone,textBalance,textSent,textReceived;
    private BarChartView chart;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_profile);
        Anim.zoomIn(this,findViewById(R.id.imgProfile)); Anim.fadeIn(this,findViewById(R.id.textName));
        textName=findViewById(R.id.textName);textPhone=findViewById(R.id.textPhone);textBalance=findViewById(R.id.textBalance);textSent=findViewById(R.id.textSent);textReceived=findViewById(R.id.textReceived);chart=findViewById(R.id.chartSpending);
        phone=getIntent().getStringExtra(HomeActivity.KEY_PHONE);if(phone==null)phone="";textPhone.setText(phone);
        ((Button)findViewById(R.id.btnBack)).setOnClickListener(v->Anim.close(this));
        ((Button)findViewById(R.id.btnEditName)).setOnClickListener(v->showEditNameDialog());
        ((Button)findViewById(R.id.btnMyQr)).setOnClickListener(v->{Intent i=new Intent(this,QrActivity.class);i.putExtra(HomeActivity.KEY_PHONE,phone);startActivity(i);});
        ((Button)findViewById(R.id.btnTheme)).setOnClickListener(v->toggleTheme());
        ((Button)findViewById(R.id.btnReport)).setOnClickListener(v->{Intent i=new Intent(this,ReportActivity.class);i.putExtra(HomeActivity.KEY_PHONE,phone);Anim.open(this,i);});
        ((Button)findViewById(R.id.btnChangePin)).setOnClickListener(v->showChangePinDialog());
        ((Button)findViewById(R.id.btnSupport)).setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+SUPPORT_PHONE)));}catch(ActivityNotFoundException e){Toast.makeText(this,"No phone app found",Toast.LENGTH_SHORT).show();}});
        ((Button)findViewById(R.id.btnLogout)).setOnClickListener(v->{FirebaseAuth.getInstance().signOut();Anim.openClear(this,new Intent(this,MainActivity.class));});
        ((Button)findViewById(R.id.btnDelete)).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Account deletion").setMessage("For account safety, deletion must be handled by support. Contact CashWing support to request account closure.").setPositiveButton("OK",null).show());
    }
    @Override protected void onResume(){super.onResume();loadProfile();}
    private void loadProfile(){
        cloud.profile().addOnSuccessListener(p->{String n=p.getString("displayName");textName.setText(n==null?"CashWing user":n);String ph=p.getString("phoneE164");if(ph!=null)textPhone.setText(PhoneUtil.local(ph));}).addOnFailureListener(e->textName.setText("Profile unavailable"));
        cloud.wallet().addOnSuccessListener(w->textBalance.setText("Balance: "+DatabaseHelper.money(CloudWalletRepository.egp(w)))).addOnFailureListener(e->textBalance.setText("Balance unavailable"));
        cloud.sentTransactions().addOnSuccessListener(sent->cloud.receivedTransactions().addOnSuccessListener(received->{
            double sentTotal=0,receivedTotal=0;double[] days=new double[7];String[] labels=DatabaseHelper.lastDays(7);Calendar today=Calendar.getInstance();
            for(DocumentSnapshot d:sent.getDocuments()){long p=d.getLong("amountPiasters")==null?0:d.getLong("amountPiasters");sentTotal+=p/100.0;com.google.firebase.Timestamp t=d.getTimestamp("createdAt");if(t!=null){Calendar c=Calendar.getInstance();c.setTime(t.toDate());long diff=daysBetween(c,today);if(diff>=0&&diff<7)days[6-(int)diff]+=p/100.0;}}
            for(DocumentSnapshot d:received.getDocuments()){long p=d.getLong("amountPiasters")==null?0:d.getLong("amountPiasters");receivedTotal+=p/100.0;}
            textSent.setText("Total sent: "+DatabaseHelper.money(sentTotal));textReceived.setText("Total received: "+DatabaseHelper.money(receivedTotal));chart.setData(days,labels);
        }));
    }
    private long daysBetween(Calendar a,Calendar b){Calendar x=(Calendar)a.clone(),y=(Calendar)b.clone();x.set(Calendar.HOUR_OF_DAY,0);x.set(Calendar.MINUTE,0);x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);y.set(Calendar.HOUR_OF_DAY,0);y.set(Calendar.MINUTE,0);y.set(Calendar.SECOND,0);y.set(Calendar.MILLISECOND,0);return (y.getTimeInMillis()-x.getTimeInMillis())/86400000L;}
    private void showEditNameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Your display name");
        input.setText(textName.getText().toString());
        new AlertDialog.Builder(this).setTitle("Edit display name").setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.length() < 2 || name.length() > 40) { Toast.makeText(this, "Name must be 2–40 characters", Toast.LENGTH_LONG).show(); return; }
                com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) { Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show(); return; }
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .update("displayName", name).addOnSuccessListener(v -> { textName.setText(name); Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show(); })
                    .addOnFailureListener(e -> Toast.makeText(this, "Could not update name: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show());
            }).setNegativeButton("Cancel", null).show();
    }

    private void toggleTheme() {
        android.content.SharedPreferences prefs = getSharedPreferences("cashwing_settings", MODE_PRIVATE);
        boolean dark = prefs.getBoolean("dark_mode", false);
        boolean next = !dark;
        prefs.edit().putBoolean("dark_mode", next).apply();
        AppCompatDelegate.setDefaultNightMode(next ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void showChangePinDialog(){
        View view=getLayoutInflater().inflate(R.layout.dialog_change_pin,null);
        EditText oldPin=view.findViewById(R.id.editOldPin),newPin=view.findViewById(R.id.editNewPin);
        new AlertDialog.Builder(this).setTitle("Change account PIN").setView(view).setPositiveButton("Save",(d,w)->{
            String oldValue=oldPin.getText().toString().trim(),newValue=newPin.getText().toString().trim();
            if(!oldValue.matches("\\d{6}") || !newValue.matches("\\d{6}")){
                Toast.makeText(this,"Both PINs must be 6 digits",Toast.LENGTH_LONG).show(); return;
            }
            com.google.firebase.auth.FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
            if(user==null || user.getEmail()==null){Toast.makeText(this,"Please log in again",Toast.LENGTH_LONG).show();return;}
            user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(),oldValue)).addOnSuccessListener(x ->
                user.updatePassword(newValue).addOnSuccessListener(v -> {
                    try { DevicePinManager.save(this,user.getUid(),newValue); } catch(Exception ignored) {}
                    Toast.makeText(this,"Account PIN changed successfully",Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> Toast.makeText(this,"Could not change PIN: "+e.getLocalizedMessage(),Toast.LENGTH_LONG).show())
            ).addOnFailureListener(e -> Toast.makeText(this,"Current PIN is incorrect",Toast.LENGTH_LONG).show());
        }).setNegativeButton("Cancel",null).show();
    }
}
