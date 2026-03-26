package com.android.update;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class IntegrationsActivity extends AppCompatActivity {

    private void showPremiumToast() {
        Toast.makeText(this, "UNLOCK PREMIUM for this feature", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_integrations);

        // Back button logic
        ImageButton backButton = findViewById(R.id.btn_back);
        backButton.setOnClickListener(v -> onBackPressed());

        // Find all clickable boxes (Info, Exchanges, Onetap Withdraw)
        int[] boxIds = new int[]{
                R.id.bsc_scan, R.id.coinmarketcap, R.id.gecko_terminal,
                R.id.coin_ranking, R.id.dex_screener, R.id.dextools,
                R.id.defi, R.id.tokenview, R.id.apespace,

                R.id.exchange_jupiter, R.id.pancakeswap, R.id.dodo,
                R.id.exchange_binance,

                R.id.wallet_jupiter, R.id.metamask, R.id.bnb_chain,
                R.id.wallet_binance, R.id.safepal, R.id.bitget, R.id.trust_wallet
        };

        // Assign toast to each
        for (int id : boxIds) {
            LinearLayout box = findViewById(id);
            if (box != null) {
                box.setOnClickListener(v -> showPremiumToast());
            }
        }
    }
}
