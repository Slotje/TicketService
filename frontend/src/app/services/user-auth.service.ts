import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, map } from 'rxjs';
import { Router } from '@angular/router';

export interface UserAuthResponse {
  token: string | null;
  email: string;
  name: string;
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

  get name(): string | null {
    return localStorage.getItem('user_name');
  }

  register(email: string, password: string, name: string): Observable<UserAuthResponse> {
    return this.http.post<UserAuthResponse>(`${this.baseUrl}/register`, { email, password, name }).pipe(
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
    localStorage.removeItem('user_name');
    this.loggedIn$.next(false);
    this.router.navigate(['/']);
  }

  verify(): Observable<boolean> {
    if (!this.token) return of(false);
    return this.http.get<UserAuthResponse>(`${this.baseUrl}/verify`, {
      headers: { Authorization: `Bearer ${this.token}` }
    }).pipe(
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
    localStorage.setItem('user_name', res.name);
    this.loggedIn$.next(true);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem('user_token');
  }
}
