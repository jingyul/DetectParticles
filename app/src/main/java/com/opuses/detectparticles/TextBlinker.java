package com.opuses.detectparticles;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.TextView;

/**
 * Created by jingyuli on 1/4/16.
 */
public class TextBlinker {
    private TextView _textView;
    private ObjectAnimator _animator;
    private ColorStateList _originalColor;

    public TextBlinker(TextView textView) {
        _textView = textView;
        _originalColor = textView.getTextColors();

        _animator = ObjectAnimator.ofInt(_textView, "textColor", Color.RED, Color.TRANSPARENT);
        _animator.setDuration(500);
        _animator.setEvaluator(new ArgbEvaluator());
        _animator.setRepeatCount(ValueAnimator.INFINITE);
        _animator.setRepeatMode(ValueAnimator.REVERSE);
    }

    public void startBlink()
    {
        _animator.start();
    }

    public void stopBlink()
    {
        _animator.cancel();
        _textView.setTextColor(_originalColor);
    }
}
