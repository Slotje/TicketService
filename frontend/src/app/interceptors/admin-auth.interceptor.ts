import { HttpInterceptorFn } from '@angular/common/http';

const ADMIN_API_PATHS = ['/api/customers', '/api/events', '/api/orders/event', '/api/auth/users'];

export const adminAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('admin_token');

  if (token && isAdminApiCall(req.url, req.method)) {
    const authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(authReq);
  }

  return next(req);
};

function isAdminApiCall(url: string, method: string): boolean {
  // Don't intercept public GET endpoints
  if (method === 'GET' && (url.includes('/api/events/published') || url.includes('/api/events/customer/'))) {
    return false;
  }
  // Single event GET for event detail page is public
  if (method === 'GET' && url.match(/\/api\/events\/\d+$/)) {
    return false;
  }

  return ADMIN_API_PATHS.some(path => url.includes(path));
}
