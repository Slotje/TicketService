import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, map } from 'rxjs';
import { Router } from '@angular/router';

export interface LoginResponse {
  token: string;
  displayName: string;
  username: string;
}

export interface ScannerUser {
  id: number;
  username: string;
  displayName: string;
  active: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private baseUrl = '/api/auth';
  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  get isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  get isLoggedIn(): boolean {
    return this.loggedIn$.value;
  }

  get token(): string | null {
    return localStorage.getItem('scanner_token');
  }

  get displayName(): string {
    return localStorage.getItem('scanner_display_name') || '';
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { username, password }).pipe(
      tap(res => {
        localStorage.setItem('scanner_token', res.token);
        localStorage.setItem('scanner_display_name', res.displayName || res.username);
        this.loggedIn$.next(true);
      })
    );
  }

  logout() {
    localStorage.removeItem('scanner_token');
    localStorage.removeItem('scanner_display_name');
    this.loggedIn$.next(false);
    this.router.navigate(['/scan/login']);
  }

  verify(): Observable<boolean> {
    if (!this.token) return of(false);
    return this.http.get<LoginResponse>(`${this.baseUrl}/verify`, {
      headers: { Authorization: `Bearer ${this.token}` }
    }).pipe(
      map(() => true),
      catchError(() => {
        this.logout();
        return of(false);
      })
    );
  }

  // Admin methods for managing scanner users
  getUsers(): Observable<ScannerUser[]> {
    return this.http.get<ScannerUser[]>(`${this.baseUrl}/users`);
  }

  createUser(username: string, password: string, displayName: string): Observable<ScannerUser> {
    return this.http.post<ScannerUser>(`${this.baseUrl}/users`, { username, password, displayName });
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/users/${id}`);
  }

  toggleUser(id: number): Observable<ScannerUser> {
    return this.http.patch<ScannerUser>(`${this.baseUrl}/users/${id}/toggle`, {});
  }

  private hasToken(): boolean {
    return !!localStorage.getItem('scanner_token');
  }
}
