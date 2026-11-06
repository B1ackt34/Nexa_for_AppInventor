package org.nexa.appinventor.probe;

import android.app.Activity;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Android UI controls, not Nexa APIs or a boundary against same-process code. */
final class SensitiveScreen {
  private final Activity activity;
  private final Set<TextView> secretViews = Collections.newSetFromMap(new WeakHashMap<>());
  SensitiveScreen(Activity activity) { this.activity = activity; protect(); }
  void protect() { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); }
  boolean protectedScreen() {
    return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
  }
  void trackPhrase(String phrase) { visit(activity.getWindow().getDecorView(), phrase, false); }
  void clear() {
    for (TextView view : secretViews) view.setText("");
    secretViews.clear();
    visit(activity.getWindow().getDecorView(), null, true);
  }
  private void visit(View view, String phrase, boolean clearPasswords) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      if (phrase != null && !phrase.isEmpty() && text.getText().toString().contains(phrase)) {
        secretViews.add(text);
      }
      if (clearPasswords && isPassword(text)) text.setText("");
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) visit(group.getChildAt(i), phrase, clearPasswords);
    }
  }
  private boolean isPassword(TextView view) {
    if (view.getTransformationMethod() instanceof PasswordTransformationMethod) return true;
    int type = view.getInputType();
    int kind = type & InputType.TYPE_MASK_CLASS;
    int variation = type & InputType.TYPE_MASK_VARIATION;
    return (kind == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        || (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
  }
}
