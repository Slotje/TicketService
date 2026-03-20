import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, map } from 'rxjs';
import { Router } from '@angular/router';

export interface AdminLoginResponse {
  token: string;
  email: string;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminAuthService {
  private baseUrl = '/api/admin/auth';
  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  get isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  get isLoggedIn(): boolean {
    return this.loggedIn$.value;
  }

  get token(): string | null {
    return localStorage.getItem('admin_token');
  }

  get displayName(): string {
    return localStorage.getItem('admin_display_name') || '';
  }

  login(email: string, password: string): Observable<AdminLoginResponse> {
    return this.http.post<AdminLoginResponse>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => {
        localStorage.setItem('admin_token', res.token);
        localStorage.setItem('admin_display_name', res.name || res.email);
        this.loggedIn$.next(true);
      })
    );
  }

  logout() {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_display_name');
    this.loggedIn$.next(false);
    this.router.navigate(['/admin/login']);
  }

  verify(): Observable<boolean> {
    if (!this.token) return of(false);
    return this.http.get<AdminLoginResponse>(`${this.baseUrl}/verify`, {
      headers: { Authorization: `Bearer ${this.token}` }
    }).pipe(
      map(() => true),
      catchError(() => {
        this.logout();
        return of(false);
      })
    );
  }

  private hasToken(): boolean {
    return !!localStorage.getItem('admin_token');
  }
}
