import { FormControl, ValidatorFn, Validators } from '@angular/forms';

export const trimmedEmailValidator: ValidatorFn = (control) => {
  const value = control.value;
  if (typeof value !== 'string' || value === '') return null;
  if (!value.trim()) return { email: true };
  return Validators.email(new FormControl(value.trim()));
};
