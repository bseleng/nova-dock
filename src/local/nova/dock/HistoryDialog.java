package local.nova.dock;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Only visible rows allocate views. The stored buffer is bounded on append/migration, never by a timer. */
final class HistoryDialog {
    private final Activity activity;
    final NativeDialog panel;
    private final Entries adapter=new Entries();
    private final TextView summary;
    private final Runnable changed=this::reload;
    private String shown;
    HistoryDialog(Activity activity,View returnFocus) {
        this.activity=activity; panel=new NativeDialog(activity,R.string.history_title,returnFocus,true);
        panel.setLeftHoldAction(R.string.action_clear,R.id.action_clear_log,this::clear);
        LinearLayout metadata=new LinearLayout(activity); metadata.setGravity(Gravity.CENTER_VERTICAL);
        metadata.setPadding(0,dp(4),0,dp(8));
        TextView subtitle=NativeDialog.text(activity,activity.getString(R.string.history_newest_first),10,NativeDialog.MUTED);
        subtitle.setId(R.id.journal_order);
        metadata.addView(subtitle,new LinearLayout.LayoutParams(0,-2,1));
        summary=NativeDialog.text(activity,"",10,NativeDialog.MUTED); summary.setId(R.id.journal_summary); summary.setGravity(Gravity.END);
        LinearLayout.LayoutParams statsLayout=new LinearLayout.LayoutParams(-2,-2); statsLayout.leftMargin=dp(12);
        metadata.addView(summary,statsLayout); panel.root.addView(metadata,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout listArea=NativeDialog.column(activity);
        ListView list=new ListView(activity); list.setId(R.id.journal_list); list.setAdapter(adapter); list.setItemsCanFocus(false);
        GradientDrawable selection=NativeDialog.shape(activity,Color.TRANSPARENT,15); selection.setStroke(dp(2),NativeDialog.ACCENT);
        list.setDivider(new ColorDrawable(0)); list.setDividerHeight(dp(6)); list.setSelector(selection); list.setDrawSelectorOnTop(true); list.setCacheColorHint(Color.TRANSPARENT);
        TextView empty=NativeDialog.text(activity,activity.getString(R.string.history_empty),13,NativeDialog.MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(14),dp(24),dp(14),dp(24));
        listArea.addView(list,new LinearLayout.LayoutParams(-1,-1)); listArea.addView(empty,new LinearLayout.LayoutParams(-1,-1)); list.setEmptyView(empty);
        panel.root.addView(listArea,new LinearLayout.LayoutParams(-1,0,1));
        panel.dismissed=()->DockService.unobserve(changed); DockService.observe(changed); reload(); panel.show(true,null);
    }
    private void reload() {
        LogBuffer buffer=Store.history(activity);
        if (!buffer.data().equals(shown)) {
            shown=buffer.data(); adapter.items.clear(); for (String line:buffer.records()) adapter.items.add(LogText.item(activity,line)); adapter.notifyDataSetChanged();
        }
        summary.setText(activity.getString(R.string.history_summary,AppText.entries(activity,buffer.count()),AppText.bytes(activity,buffer.bytes()),AppText.bytes(activity,LogBuffer.MAX_BYTES)));
    }
    private void clear() {
        Store.clearHistory(activity); reload();
    }
    void dismiss() { panel.dialog.dismiss(); }
    private int dp(int value) { return NativeDialog.dp(activity,value); }
    private final class Entries extends BaseAdapter {
        final List<LogText.Item> items=new ArrayList<>();
        public int getCount() { return items.size(); }
        public Object getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position,View convert,ViewGroup parent) {
            Row holder;
            if (convert==null) { holder=new Row(); convert=holder.root; convert.setTag(holder); } else holder=(Row)convert.getTag();
            LogText.Item item=items.get(position); holder.time.setText(item.time); holder.title.setText(item.title); holder.badge.setText(item.badge); holder.badge.setTextColor(item.color);
            holder.badge.setBackground(NativeDialog.shape(activity,(item.color&0x00ffffff)|0x18000000,8));
            holder.detail.setText(item.detail); holder.detail.setVisibility(item.detail.isEmpty()?View.GONE:View.VISIBLE);
            holder.root.setContentDescription(item.time+". "+item.title+". "+item.badge+(item.detail.isEmpty()?"":". "+item.detail)); return convert;
        }
    }
    private final class Row {
        final LinearLayout root;
        final TextView time,title,badge,detail;
        Row() {
            root=new LinearLayout(activity); root.setGravity(Gravity.TOP); root.setPadding(dp(10),dp(10),dp(10),dp(10)); root.setBackground(NativeDialog.shape(activity,0xff233236,15));
            root.setClipToOutline(true); root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            time=NativeDialog.text(activity,"",16,NativeDialog.MUTED); time.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL); time.setSingleLine(true);
            int width=Math.max(dp(84),(int)Math.ceil(time.getPaint().measureText("00:00:00"))+dp(10)); root.addView(time,new LinearLayout.LayoutParams(width,-2));
            LinearLayout content=NativeDialog.column(activity); LinearLayout heading=new LinearLayout(activity); heading.setGravity(Gravity.CENTER_VERTICAL);
            title=NativeDialog.text(activity,"",13,NativeDialog.TEXT); title.setTypeface(null,Typeface.BOLD); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            badge=NativeDialog.text(activity,"",8,NativeDialog.ACCENT); badge.setTypeface(null,Typeface.BOLD); badge.setPadding(dp(7),dp(3),dp(7),dp(3));
            LinearLayout.LayoutParams tag=new LinearLayout.LayoutParams(-2,-2); tag.leftMargin=dp(8); heading.addView(badge,tag); content.addView(heading);
            detail=NativeDialog.text(activity,"",11,NativeDialog.MUTED); detail.setTextIsSelectable(false); detail.setPadding(0,dp(4),0,0); detail.setLineSpacing(dp(2),1); content.addView(detail);
            for (TextView text:new TextView[]{time,title,badge,detail}) { text.setFocusable(false); text.setClickable(false); text.setLongClickable(false); text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
            root.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
}
