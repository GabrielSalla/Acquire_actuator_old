package com.example.acquire.graph;

import com.example.acquire.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class CheckBoxAdapter extends ArrayAdapter<Model>{
	private Model[] modelItems = null;
	private LayoutInflater inflater=null;
	
	private class ViewHolder{
		CheckBox cb;
		TextView name;
	}
	
	public CheckBoxAdapter(Context context, Model[] resource){
		super(context, R.layout.list_row, resource);
		this.modelItems = resource;
		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	@Override
	public View getView(final int position, View convertView, ViewGroup parent){
		View view = convertView;
		final ViewHolder holder;
		if(convertView == null){
			view = inflater.inflate(R.layout.list_row, parent, false);
			holder = new ViewHolder();
			holder.name = (TextView)view.findViewById(R.id.textViewName);
			holder.cb = (CheckBox)view.findViewById(R.id.checkBoxHold);
			
			view.setTag(holder);
		}
		else{
			holder = (ViewHolder)view.getTag();
		}
		
		holder.name.setText(modelItems[position].getName());
		holder.cb.setChecked(modelItems[position].getValue());
		
		holder.cb.setChecked(modelItems[position].getValue());
		
		holder.cb.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v){
				modelItems[position].setValue(holder.cb.isChecked());
			}
		});
		
		holder.cb.setFocusableInTouchMode(false);
		holder.cb.setClickable(false);
		holder.cb.setFocusable(false);
		
		return view;
	}
}
