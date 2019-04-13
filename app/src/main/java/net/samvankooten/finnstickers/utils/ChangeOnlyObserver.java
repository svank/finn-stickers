package net.samvankooten.finnstickers.utils;

import androidx.lifecycle.Observer;

/**
 * When observers are attached to LiveDatas, they are immediately called with the current
 * value of the LiveData. Sometimes that is undesirable. This class wraps an Observer
 * and ignores that first call.
 */
public class ChangeOnlyObserver<T> implements Observer<T> {
    private Observer<? super T> observer;
    private boolean shouldFire = false;
    public ChangeOnlyObserver(Observer<? super T> observer) {
        this.observer = observer;
    }
    
    @Override
    public void onChanged(T value) {
        if (shouldFire)
            observer.onChanged(value);
        else
            shouldFire = true;
    }
}
