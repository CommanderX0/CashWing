package com.example.quickpay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {
    public static final String KEY_RECEIVER = "RECEIVER";
    public static final String KEY_AMOUNT = "AMOUNT";
    private final CloudWalletRepository cloud = new CloudWalletRepository();
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        Anim.fadeIn(this, findViewById(R.id.textTitle)); Anim.zoomIn(this, findViewById(R.id.imgCheck)); Anim.zoomIn(this, findViewById(R.id.textAmount));
        TextView textAmount=findViewById(R.id.textAmount), textReceiver=findViewById(R.id.textReceiver), textNewBalance=findViewById(R.id.textNewBalance);
        View[] money={findViewById(R.id.money1),findViewById(R.id.money2),findViewById(R.id.money3),findViewById(R.id.money4),findViewById(R.id.money5)};
        Button btnCall=findViewById(R.id.btnCall), btnHome=findViewById(R.id.btnHome);
        String phone=getIntent().getStringExtra(HomeActivity.KEY_PHONE), receiver=getIntent().getStringExtra(KEY_RECEIVER), amount=getIntent().getStringExtra(KEY_AMOUNT);
        if(phone==null) phone=""; if(receiver==null) receiver="Unknown"; if(amount==null) amount="0 EGP";
        final String receiverPhone=receiver, userPhone=phone;
        textAmount.setText(amount); textReceiver.setText("Sent to " + receiver);
        cloud.findUser(PhoneUtil.toE164(receiver)).addOnSuccessListener(result -> {
            String n=result.isEmpty()?"CashWing user":result.getDocuments().get(0).getString("displayName");
            textReceiver.setText("Sent to " + (n==null?"CashWing user":n) + " (" + receiverPhone + ")");
        });
        cloud.wallet().addOnSuccessListener(w -> textNewBalance.setText("New balance: " + DatabaseHelper.money(CloudWalletRepository.egp(w))))
                .addOnFailureListener(e -> textNewBalance.setText("Transfer completed"));
        Anim.flyMoney(this,money,true);
        btnCall.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+receiverPhone)); try{startActivity(i);}catch(ActivityNotFoundException e){Toast.makeText(this,"No phone app found",Toast.LENGTH_SHORT).show();} });
        btnHome.setOnClickListener(v -> goHome(userPhone));
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){goHome(userPhone);}});
    }
    private void goHome(String phone){Intent i=new Intent(this,HomeActivity.class);i.putExtra(HomeActivity.KEY_PHONE,phone);Anim.openHome(this,i);}
}
