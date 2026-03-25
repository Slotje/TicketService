import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, map } from 'rxjs';
import { Router } from '@angular/router';

export interface CustomerLoginResponse {
  token: string;
  customerId: number;
  companyName: string;
  contactPerson: string;
  email: string;
}

@Injectable({
  providedIn: 'root'
})
export class CustomerAuthService {
  private baseUrl = '/api/customer/auth';
  private loggedIn$ = new BehaviorSubject<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  get isLoggedIn$(): Observable<boolean> {
    return this.loggedIn$.asObservable();
  }

  get isLoggedIn(): boolean {
    return this.loggedIn$.value;
  }

  get token(): string | null {
    return localStorage.getItem('customer_token');
  }

  get customerId(): number | null {
    const id = localStorage.getItem('customer_id');
    return id ? parseInt(id) : null;
  }

  get companyName(): string {
    return localStorage.getItem('customer_company_name') || '';
  }

  get contactPerson(): string {
    return localStorage.getItem('customer_contact_person') || '';
  }

  login(email: string, password: string): Observable<CustomerLoginResponse> {
    return this.http.post<CustomerLoginResponse>(`${this.baseUrl}/login`, { email, password }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  setPassword(token: string, password: string): Observable<CustomerLoginResponse> {
    return this.http.post<CustomerLoginResponse>(`${this.baseUrl}/set-password`, { token, password }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  verifyInvite(token: string): Observable<{ companyName: string; email: string }> {
    return this.http.get<{ companyName: string; email: string }>(`${this.baseUrl}/invite/${token}`);
  }

  private storeAuth(res: CustomerLoginResponse) {
    localStorage.setItem('customer_token', res.token);
    localStorage.setItem('customer_id', res.customerId.toString());
    localStorage.setItem('customer_company_name', res.companyName);
    if (res.contactPerson) {
      localStorage.setItem('customer_contact_person', res.contactPerson);
    }
    this.loggedIn$.next(true);
  }

  logout() {
    localStorage.removeItem('customer_token');
    localStorage.removeItem('customer_id');
    localStorage.removeItem('customer_company_name');
    localStorage.removeItem('customer_contact_person');
    localStorage.removeItem('customer_slug');
    localStorage.removeItem('ts_cart');
    this.loggedIn$.next(false);
    this.router.navigate(['/klant/login']);
  }

  verify(): Observable<boolean> {
    if (!this.token) return of(false);
    return this.http.get<CustomerLoginResponse>(`${this.baseUrl}/verify`, {
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
    return !!localStorage.getItem('customer_token');
  }
}
