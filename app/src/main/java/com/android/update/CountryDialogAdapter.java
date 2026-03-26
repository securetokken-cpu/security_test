package com.android.update;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CountryDialogAdapter extends RecyclerView.Adapter<CountryDialogAdapter.ViewHolder> {

    private List<String> countryList;
    private List<String> filteredList;
    private OnCountrySelectedListener listener;

    public CountryDialogAdapter(List<String> countryList, OnCountrySelectedListener listener) {
        this.countryList = countryList;
        this.filteredList = new ArrayList<>(countryList);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.countryName.setText(filteredList.get(position));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCountrySelected(filteredList.get(position));
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(countryList);
        } else {
            for (String country : countryList) {
                if (country.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(country);
                }
            }
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView countryName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            countryName = itemView.findViewById(android.R.id.text1);
        }
    }

    public interface OnCountrySelectedListener {
        void onCountrySelected(String country);
    }
}
