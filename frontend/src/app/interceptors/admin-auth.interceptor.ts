import { HttpInterceptorFn } from '@angular/common/http';

const ADMIN_API_PATHS = ['/api/customers', '/api/events', '/api/orders/event', '/api/auth/users'];
const CUSTOMER_API_PATHS = ['/api/events/my', '/api/customer/auth/verify', '/api/customer/auth/branding'];

export const adminAuthInterceptor: HttpInterceptorFn = (req, next) => {
  // Customer token for customer-specific endpoints
  const customerToken = localStorage.getItem('customer_token');
  if (customerToken && isCustomerApiCall(req.url)) {
    const authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${customerToken}` }
    });
    return next(authReq);
  }

  // Admin token for admin endpoints
  const adminToken = localStorage.getItem('admin_token');
  if (adminToken && isAdminApiCall(req.url, req.method)) {
    const authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${adminToken}` }
    });
    return next(authReq);
  }

  return next(req);
};

function isCustomerApiCall(url: string): boolean {
  return CUSTOMER_API_PATHS.some(path => url.includes(path));
}

function isAdminApiCall(url: string, method: string): boolean {
  // Don't intercept customer-specific endpoints
  if (isCustomerApiCall(url)) {
    return false;
  }
  // Don't intercept public GET endpoints
  if (method === 'GET' && (url.includes('/api/events/published') || url.includes('/api/events/customer/'))) {
    return false;
  }
  // Single event GET for event detail page is public
  if (method === 'GET' && url.match(/\/api\/events\/\d+$/)) {
    return false;
  }
  // Customer slug lookup is public
  if (method === 'GET' && url.includes('/api/customers/slug/')) {
    return false;
  }
  // Image serving is public
  if (method === 'GET' && url.includes('/api/images/')) {
    return false;
  }
  // Image upload adds its own auth header manually
  if (method === 'POST' && url.includes('/api/images/upload')) {
    return false;
  }

  return ADMIN_API_PATHS.some(path => url.includes(path));
}
