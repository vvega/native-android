package com.tealeaf;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View.OnKeyListener;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import com.tealeaf.event.InputPromptKeyUpEvent;
import com.tealeaf.event.InputPromptMoveEvent;
import android.view.View;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.InputFilter;
import android.app.Activity;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.content.Context;
import android.view.KeyEvent;
import android.os.Handler;
import android.util.AttributeSet;
import com.tealeaf.util.ILogger;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;

/**
 * Allows JS to open up a keyboard that has an EditText attached
 * above the keyboard. Key strokes are propagated to JS so that the 
 * GL layer can be updated. It allows for different input types, length
 * constraints, and the ability to move back and forth between TextEditViews.
 */
public class TextEditViewHandler {

	private Activity activity;
	private View editTextHandler;
	private TextEditView editText;
	private boolean isActive = false;
	private boolean registerTextChange = true;
	private InputName inputName = InputName.DEFAULT;
	private boolean hasForward = false;

	public enum InputName {
		DEFAULT,
		NUMBER,
		PHONE,
		PASSWORD,
		CAPITAL
	}

	public TextEditViewHandler(Activity activity) {
		this.activity = activity;

		LayoutInflater inflater = activity.getLayoutInflater();
		editTextHandler = inflater.inflate(R.layout.edit_text_handler, null);
		editTextHandler.setOnClickListener(this.getScreenCaptureListener());

		// setup EditText
		editText = (TextEditView) editTextHandler.findViewById(R.id.handler_text);
		editText.setTextEditViewHandler(this);
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				// propagate text changes to JS to update views
				if (registerTextChange) {
					logger.log("KeyUp textChange in TextEditView");
					EventQueue.pushEvent(new InputPromptKeyUpEvent(s.toString()));
				} else {
					registerTextChange = true;
				}
			} 

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			
			}
		});
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					deactivate();	
				} else if (actionId == EditorInfo.IME_ACTION_NEXT) {
					EventQueue.pushEvent(new InputPromptMoveEvent(true));	
				}

				return false;
			}
		});

		// setup forward and back keys
		View backButton = editTextHandler.findViewById(R.id.back_button);
		View forwardButton = editTextHandler.findViewById(R.id.forward_button);
		View doneButton = editTextHandler.findViewById(R.id.done_button);

		doneButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				closeKeyboard();
			}
		});
		backButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EventQueue.pushEvent(new InputPromptMoveEvent(false));	
			}
		});
		forwardButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				EventQueue.pushEvent(new InputPromptMoveEvent(true));	
			}
		});

		// attach above GL layer
		RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT,
																		  RelativeLayout.LayoutParams.FILL_PARENT);

		activity.addContentView(editTextHandler, rlp);
	}

	/**
	 * Used to determine when the keyboard has gone away.
	 */
	public void onBackPressed() {
		deactivate();
	}

	/**
	 * Perform actions necessary when TextEditView pops up.
	 */
	public void activate(String text, String hint, boolean hasBackward, boolean hasForward, String inputType, int maxLength) {

		editText.setImeOptions(hasForward ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);

		if (!isActive) {
			isActive = true;
			editTextHandler.setVisibility(View.VISIBLE);
			
			Animation animFadeIn = AnimationUtils.loadAnimation(activity.getApplicationContext(),
																android.R.anim.fade_in);
			animFadeIn.setDuration(250);
			editTextHandler.setAnimation(animFadeIn);


			// In order to show keyboard directly after making EditText visible we must show keyboard
			// independent of EditText and then requestFocus.
			InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);

			editText.requestFocus();
		}

		// EditText options
		int type;
		inputName = InputName.valueOf(inputType.toUpperCase().trim());

		switch (inputName) {
			case NUMBER:
				type = InputType.TYPE_CLASS_NUMBER;	
				break;
			case PHONE:
				type = InputType.TYPE_CLASS_PHONE;
				break;
			case PASSWORD:
				type = InputType.TYPE_TEXT_VARIATION_PASSWORD;	
				break;
			case CAPITAL:
				type = InputType.TYPE_TEXT_FLAG_CAP_WORDS;
				break;
			default:
				type = InputType.TYPE_CLASS_TEXT;
				break;
		}

		editText.setInputType(type | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

		if (maxLength == -1) {
			editText.setFilters(new InputFilter[] {});	
		} else {
			editText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxLength) });
		}

		registerTextChange = false;

		editText.setSingleLine();
		editText.setFocusable(true);
		editText.setFocusableInTouchMode(true);
		editText.setText(text);
		editText.setHint(hint);
		editText.setSelection(editText.getText().length());

		// Button options
		View backButton = editTextHandler.findViewById(R.id.back_button);
		View forwardButton = editTextHandler.findViewById(R.id.forward_button);
		View doneButton = editTextHandler.findViewById(R.id.done_button);

		if (!hasForward && !hasBackward) {
			backButton.setVisibility(View.GONE);
			forwardButton.setVisibility(View.GONE);
			doneButton.setVisibility(View.VISIBLE);
		} else {
			backButton.setVisibility(View.VISIBLE);
			forwardButton.setVisibility(View.VISIBLE);
			doneButton.setVisibility(View.GONE);
			backButton.setEnabled(hasBackward);
			forwardButton.setEnabled(hasForward);
		}

		this.hasForward = hasForward;
	}

	/**
	 * Perform actions necessary when TextEditView is closed.
	 */
	public void deactivate() {
		if (isActive) {
			isActive = false;
			editTextHandler.setVisibility(View.GONE);
		}
	}

	public void closeKeyboard() {
		logger.log("TextEditView closeKeyboard");
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);	

		this.deactivate();	
	}

	public OnClickListener getScreenCaptureListener() {
		return new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (isActive) {
					closeKeyboard();
				}
			}
		};	
	}

	public static class TextEditView extends EditText {

		private TextEditViewHandler handler;

		public TextEditView(Context context) { super(context); }
		public TextEditView(Context context, AttributeSet attrs) { super(context, attrs); }
		public TextEditView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }

		@Override
		public boolean onKeyPreIme(int keyCode, KeyEvent event) {
			// listen for soft keyboard back button.
			// you cannot use standard onBackPressed for this key press.
			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
				if (handler != null) {
					handler.deactivate();	
				}
			}	

			return super.onKeyPreIme(keyCode, event);
		}

		/**
		 * Set instance of TextEditViewHandler to perform actions.
		 */
		public void setTextEditViewHandler(TextEditViewHandler handler) {
			this.handler = handler;	
		}
	}
}
