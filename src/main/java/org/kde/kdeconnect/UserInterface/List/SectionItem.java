package org.kde.kdeconnect.UserInterface.List;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.kde.kdeconnect_tp.R;

public class SectionItem implements ListAdapter.Item {

	private final String title;
    public boolean isSectionEmpty;

	public SectionItem(String title) {
		this.title = title;
        this.isSectionEmpty = false;
	}

    @Override
    public View inflateView(LayoutInflater layoutInflater) {

        View v = layoutInflater.inflate(R.layout.list_item_category, null);

        //Make it not selectable
        v.setOnClickListener(null);
        v.setOnLongClickListener(null);

        TextView sectionView = (TextView) v.findViewById(R.id.list_item_category_text);
        sectionView.setText(title);

        if (isSectionEmpty) {
            v.findViewById(R.id.list_item_category_empty_placeholder).setVisibility(View.VISIBLE);
        }

        return v;

    }
}
