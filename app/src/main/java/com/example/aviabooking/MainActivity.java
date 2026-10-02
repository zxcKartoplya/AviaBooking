package com.example.aviabooking;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_DEPARTURE_PICKER = "departure_picker";
    private static final String TAG_RETURN_PICKER = "return_picker";
    private static final String STATE_DEPARTURE_DATE = "departure_date";
    private static final String STATE_RETURN_DATE = "return_date";
    private static final Pattern CITY_CODE = Pattern.compile("\\(([A-Z]{3})\\)");
    private static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private final DateFormat dateFormat = createDateFormat();
    private final List<Animator> ambientAnimators = new ArrayList<>();

    private Spinner spinnerFrom;
    private Spinner spinnerTo;
    private MaterialButton buttonSwap;
    private FlightPathView flightPath;
    private TextView inputDeparture;
    private TextView inputReturn;

    @Nullable
    private Long departureDate;
    @Nullable
    private Long returnDate;
    private boolean swapInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerFrom = findViewById(R.id.spinner_from);
        spinnerTo = findViewById(R.id.spinner_to);
        buttonSwap = findViewById(R.id.button_swap);
        flightPath = findViewById(R.id.flight_path);
        inputDeparture = findViewById(R.id.input_departure);
        inputReturn = findViewById(R.id.input_return);

        applyWindowInsets();
        setUpCities(savedInstanceState);
        setUpDates(savedInstanceState);
        setUpPassengers();
        startAmbientAnimations();
        if (savedInstanceState == null) {
            playEntranceAnimation();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (departureDate != null) {
            outState.putLong(STATE_DEPARTURE_DATE, departureDate);
        }
        if (returnDate != null) {
            outState.putLong(STATE_RETURN_DATE, returnDate);
        }
    }

    @Override
    protected void onDestroy() {
        for (Animator animator : ambientAnimators) {
            animator.cancel();
        }
        ambientAnimators.clear();
        super.onDestroy();
    }

    private void applyWindowInsets() {
        View header = findViewById(R.id.header);
        View content = findViewById(R.id.content);
        View statusBarScrim = findViewById(R.id.status_bar_scrim);
        NestedScrollView scroll = findViewById(R.id.scroll);
        int headerPaddingTop = header.getPaddingTop();
        int contentPaddingBottom = content.getPaddingBottom();

        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    int range = Math.max(header.getHeight() - statusBarScrim.getHeight(), 1);
                    statusBarScrim.setAlpha(Math.min(1f, (float) scrollY / range));
                });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (root, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            ViewGroup.LayoutParams scrimParams = statusBarScrim.getLayoutParams();
            if (scrimParams.height != bars.top) {
                scrimParams.height = bars.top;
                statusBarScrim.setLayoutParams(scrimParams);
            }
            header.setPadding(header.getPaddingLeft(), headerPaddingTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            content.setPadding(bars.left, content.getPaddingTop(),
                    bars.right, contentPaddingBottom + bars.bottom);
            root.setPadding(0, 0, 0, Math.max(ime.bottom - bars.bottom, 0));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setUpCities(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            spinnerTo.setSelection(1, false);
        }
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateRoute();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        spinnerFrom.setOnItemSelectedListener(listener);
        spinnerTo.setOnItemSelectedListener(listener);
        buttonSwap.setOnClickListener(v -> swapCities());
        updateRoute();
    }

    private void updateRoute() {
        flightPath.setRoute(cityCode(spinnerFrom), cityCode(spinnerTo));
    }

    private static String cityCode(Spinner spinner) {
        Matcher matcher = CITY_CODE.matcher(String.valueOf(spinner.getSelectedItem()));
        return matcher.find() ? matcher.group(1) : "";
    }

    private void swapCities() {
        if (swapInProgress) {
            return;
        }
        swapInProgress = true;
        buttonSwap.animate()
                .rotationBy(180f)
                .setDuration(450)
                .setInterpolator(new OvershootInterpolator(1.4f))
                .start();

        float shift = dp(12);
        hideForSwap(spinnerFrom, shift).start();
        hideForSwap(spinnerTo, -shift).withEndAction(() -> {
            int fromPosition = spinnerFrom.getSelectedItemPosition();
            spinnerFrom.setSelection(spinnerTo.getSelectedItemPosition());
            spinnerTo.setSelection(fromPosition);
            revealAfterSwap(spinnerFrom, shift);
            revealAfterSwap(spinnerTo, -shift);
            swapInProgress = false;
        }).start();
    }

    private ViewPropertyAnimator hideForSwap(View view, float shift) {
        return view.animate()
                .alpha(0f)
                .translationY(shift)
                .setDuration(150)
                .setInterpolator(EMPHASIZED_ACCELERATE);
    }

    private void revealAfterSwap(View view, float shift) {
        view.setTranslationY(shift);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(EMPHASIZED)
                .start();
    }

    private void setUpDates(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_DEPARTURE_DATE)) {
                departureDate = savedInstanceState.getLong(STATE_DEPARTURE_DATE);
            }
            if (savedInstanceState.containsKey(STATE_RETURN_DATE)) {
                returnDate = savedInstanceState.getLong(STATE_RETURN_DATE);
            }
        }

        TextInputLayout layoutDeparture = findViewById(R.id.layout_departure);
        TextInputLayout layoutReturn = findViewById(R.id.layout_return);
        inputDeparture.setOnClickListener(v -> showDeparturePicker());
        inputReturn.setOnClickListener(v -> showReturnPicker());
        layoutDeparture.setStartIconOnClickListener(v -> showDeparturePicker());
        layoutReturn.setStartIconOnClickListener(v -> showReturnPicker());

        MaterialDatePicker<Long> departurePicker = findPicker(TAG_DEPARTURE_PICKER);
        if (departurePicker != null) {
            departurePicker.addOnPositiveButtonClickListener(this::onDepartureSelected);
        }
        MaterialDatePicker<Long> returnPicker = findPicker(TAG_RETURN_PICKER);
        if (returnPicker != null) {
            returnPicker.addOnPositiveButtonClickListener(this::onReturnSelected);
        }
    }

    private void showDeparturePicker() {
        MaterialDatePicker<Long> picker = buildPicker(TAG_DEPARTURE_PICKER, R.string.hint_departure_date,
                MaterialDatePicker.todayInUtcMilliseconds(), departureDate);
        if (picker != null) {
            picker.addOnPositiveButtonClickListener(this::onDepartureSelected);
        }
    }

    private void showReturnPicker() {
        long earliest = departureDate != null ? departureDate : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = buildPicker(TAG_RETURN_PICKER, R.string.hint_return_date,
                earliest, returnDate);
        if (picker != null) {
            picker.addOnPositiveButtonClickListener(this::onReturnSelected);
        }
    }

    @Nullable
    private MaterialDatePicker<Long> buildPicker(String tag, @StringRes int title,
                                                 long earliest, @Nullable Long current) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentByTag(tag) != null || fragmentManager.isStateSaved()) {
            return null;
        }
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(earliest)
                .setOpenAt(current != null ? current : earliest)
                .setValidator(DateValidatorPointForward.from(earliest))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title)
                .setSelection(current)
                .setCalendarConstraints(constraints)
                .build();
        picker.show(fragmentManager, tag);
        fragmentManager.executePendingTransactions();
        return picker;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private MaterialDatePicker<Long> findPicker(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        return fragment instanceof MaterialDatePicker ? (MaterialDatePicker<Long>) fragment : null;
    }

    private void onDepartureSelected(Long date) {
        departureDate = date;
        showDate(inputDeparture, date);
        if (returnDate != null && returnDate < date) {
            returnDate = null;
            inputReturn.setText(null);
        }
    }

    private void onReturnSelected(Long date) {
        returnDate = date;
        showDate(inputReturn, date);
    }

    private void showDate(TextView field, long date) {
        field.setText(dateFormat.format(new Date(date)));
        field.setAlpha(0f);
        field.setTranslationY(dp(8));
        field.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(EMPHASIZED)
                .start();
    }

    private void setUpPassengers() {
        bindPassengerFocus(R.id.input_adults, R.id.icon_adults);
        bindPassengerFocus(R.id.input_children, R.id.icon_children);
        bindPassengerFocus(R.id.input_infants, R.id.icon_infants);
    }

    private void bindPassengerFocus(@IdRes int inputId, @IdRes int iconId) {
        View icon = findViewById(iconId);
        findViewById(inputId).setOnFocusChangeListener((input, hasFocus) -> {
            float scale = hasFocus ? 1.3f : 1f;
            icon.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(240)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .start();
        });
    }

    private void startAmbientAnimations() {
        drift(findViewById(R.id.cloud_large), -dp(28), 9000);
        drift(findViewById(R.id.cloud_small), dp(36), 12000);
    }

    private void drift(View view, float distance, long duration) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, distance);
        animator.setDuration(duration);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
        ambientAnimators.add(animator);
    }

    private void playEntranceAnimation() {
        int[] ids = {
                R.id.header_subtitle, R.id.header_title, R.id.flight_path,
                R.id.card_route, R.id.card_dates, R.id.card_passengers, R.id.button_buy
        };
        float offset = dp(36);
        for (int i = 0; i < ids.length; i++) {
            View view = findViewById(ids[i]);
            view.setAlpha(0f);
            view.setTranslationY(offset);
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(100L + i * 80L)
                    .setDuration(650)
                    .setInterpolator(EMPHASIZED)
                    .start();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static DateFormat createDateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("d MMMM yyyy, EEEE", Locale.forLanguageTag("ru"));
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}
