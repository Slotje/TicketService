import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, map } from 'rxjs';
import { Router } from '@angular/router';

export interface UserAuthResponse {
  token: string | null;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserAuthService {
  private baseUrl = '/api/user/auth';
  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  get isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  get isLoggedIn(): boolean {
    return this.loggedIn$.value;
  }

  get token(): string | null {
    return localStorage.getItem('user_token');
  }

  get email(): string | null {
    return localStorage.getItem('user_email');
  }

  get firstName(): string | null {
    return localStorage.getItem('user_first_name');
  }

  get lastName(): string | null {
    return localStorage.getItem('user_last_name');
  }

  get name(): string | null {
    const first = this.firstName;
    const last = this.lastName;
    if (first && last) return `${first} ${last}`;
    return first || last;
  }

  get phone(): string | null {
    return localStorage.getItem('user_phone');
  }

  register(email: string, password: string, firstName: string, lastName: string, phone?: string): Observable<UserAuthResponse> {
    return this.http.post<UserAuthResponse>(`${this.baseUrl}/register`, { email, password, firstName, lastName, phone }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  login(email: string, password: string): Observable<UserAuthResponse> {
    return this.http.post<UserAuthResponse>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  logout() {
    localStorage.removeItem('user_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('user_first_name');
    localStorage.removeItem('user_last_name');
    localStorage.removeItem('user_phone');
    this.loggedIn$.next(false);
    this.router.navigate(['/']);
  }

  verify(): Observable<boolean> {
    if (!this.token) return of(false);
    return this.http.get<UserAuthResponse>(`${this.baseUrl}/verify`, {
      headers: { Authorization: `Bearer ${this.token}` }
    }).pipe(
      tap(res => this.storeAuth(res)),
      map(() => true),
      catchError(() => {
        this.logout();
        return of(false);
      })
    );
  }

  private storeAuth(res: UserAuthResponse) {
    if (res.token) {
      localStorage.setItem('user_token', res.token);
    }
    localStorage.setItem('user_email', res.email);
    localStorage.setItem('user_first_name', res.firstName);
    localStorage.setItem('user_last_name', res.lastName);
    if (res.phone) {
      localStorage.setItem('user_phone', res.phone);
    }
    this.loggedIn$.next(true);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem('user_token');
  }
}
