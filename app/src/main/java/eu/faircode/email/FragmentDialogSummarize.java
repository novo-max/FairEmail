package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2024 by Marcel Bokhorst (M66B)
*/

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.util.Date;

public class FragmentDialogSummarize extends FragmentDialogBase {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        final View view = LayoutInflater.from(context).inflate(R.layout.dialog_summarize, null);
        final TextView tvCaption = view.findViewById(R.id.tvCaption);
        final TextView tvFrom = view.findViewById(R.id.tvFrom);
        final TextView tvSubject = view.findViewById(R.id.tvSubject);
        final TextView tvSummary = view.findViewById(R.id.tvSummary);
        final ImageButton ibCopy = view.findViewById(R.id.ibCopy);
        final TextView tvElapsed = view.findViewById(R.id.tvElapsed);
        final TextView tvError = view.findViewById(R.id.tvError);
        final ContentLoadingProgressBar pbWait = view.findViewById(R.id.pbWait);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean compact = prefs.getBoolean("compact", false);
        int zoom = prefs.getInt("view_zoom", compact ? 0 : 1);
        int message_zoom = prefs.getInt("message_zoom", 100);

        float textSize = Helper.getTextSize(context, zoom) * message_zoom / 100f;
        tvSummary.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        ibCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Context context = v.getContext();
                ClipboardManager clipboard = Helper.getSystemService(context, ClipboardManager.class);
                if (clipboard == null)
                    return;

                ClipData clip = ClipData.newPlainText(getString(R.string.app_name), tvSummary.getText());
                clipboard.setPrimaryClip(clip);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                    ToastEx.makeText(context, R.string.title_clipboard_copied, Toast.LENGTH_LONG).show();

            }
        });

        Bundle args = getArguments();

        tvCaption.setText(AI.getSummarizePrompt(context));
        tvFrom.setText(args.getString("from"));
        tvSubject.setText(args.getString("subject"));

        new SimpleTask<Spanned>() {
            @Override
            protected void onPreExecute(Bundle args) {
                tvSummary.setVisibility(View.GONE);
                ibCopy.setVisibility(View.GONE);
                tvElapsed.setVisibility(View.GONE);
                tvError.setVisibility(View.GONE);
                pbWait.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onPostExecute(Bundle args) {
                pbWait.setVisibility(View.GONE);
            }

            @Override
            protected Spanned onExecute(Context context, Bundle args) throws Throwable {
                long id = args.getLong("id");

                DB db = DB.getInstance(context);
                EntityMessage message = db.message().getMessage(id);
                if (message == null || !message.content)
                    return null;

                long start = new Date().getTime();
                Spanned summary = AI.getSummaryText(context, message);
                args.putLong("elapsed", new Date().getTime() - start);

                return summary;
            }

            @Override
            protected void onExecuted(Bundle args, Spanned summary) {
                tvSummary.setText(summary);
                tvSummary.setVisibility(View.VISIBLE);
                ibCopy.setVisibility(View.VISIBLE);
                tvElapsed.setText(Helper.formatDuration(args.getLong("elapsed")));
                tvElapsed.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onException(Bundle args, Throwable ex) {
                tvError.setText(new ThrowableWrapper(ex).toSafeString());
                tvError.setVisibility(View.VISIBLE);
            }
        }.execute(this, args, "message:summarize");

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(android.R.string.cancel, null);

        return builder.create();
    }

    public static void summarize(EntityMessage message, FragmentManager fm) {
        Bundle args = new Bundle();
        args.putLong("id", message.id);
        args.putString("from", MessageHelper.formatAddresses(message.from));
        args.putString("subject", message.subject);

        FragmentDialogSummarize fragment = new FragmentDialogSummarize();
        fragment.setArguments(args);
        fragment.show(fm, "message:summary");
    }
}
