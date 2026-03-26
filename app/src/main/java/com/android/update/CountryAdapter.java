package com.android.update;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.update.R;
import com.android.update.Country;

import java.util.ArrayList;
import java.util.List;

public class CountryAdapter extends RecyclerView.Adapter<CountryAdapter.CountryViewHolder> {

    private List<Country> originalCountries;
    private List<Country> filteredCountries;
    private OnCountrySelectedListener listener;

    public CountryAdapter(List<Country> countries, OnCountrySelectedListener listener) {
        this.originalCountries = countries;
        this.filteredCountries = new ArrayList<>(countries);
        this.listener = listener;
    }

    @NonNull
    @Override
    public CountryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
        return new CountryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CountryViewHolder holder, int position) {
        Country country = filteredCountries.get(position);
        holder.bind(country);
    }

    @Override
    public int getItemCount() {
        return filteredCountries.size();
    }

    public void filterCountries(String query) {
        filteredCountries.clear();
        if (query.isEmpty()) {
            filteredCountries.addAll(originalCountries);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Country country : originalCountries) {
                if (country.getName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredCountries.add(country);
                }
            }
        }
        notifyDataSetChanged();
    }

    public class CountryViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvCountryName;
        private final TextView tvCountryCode;

        public CountryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCountryName = itemView.findViewById(R.id.tvCountryName);
            tvCountryCode = itemView.findViewById(R.id.tvCountryCode);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onCountrySelected(filteredCountries.get(position));
                }
            });
        }

        public void bind(Country country) {
            tvCountryName.setText(country.getName());
            tvCountryCode.setText(country.getCode());
        }
    }

    public interface OnCountrySelectedListener {
        void onCountrySelected(Country country);
    }
}