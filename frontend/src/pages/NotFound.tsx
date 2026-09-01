/**
 * 404 page. Plain error view; link back to /dashboard.
 */
import { Link } from 'react-router-dom';

export function NotFound() {
  return (
    <div className="error-page">
      <div className="error-page__card">
        <div className="error-page__code">404</div>
        <h1 className="error-page__title">Page not found</h1>
        <p className="error-page__message">
          The page you were looking for doesn&apos;t exist.
        </p>
        <Link to="/dashboard" className="btn btn--primary">
          Back to Dashboard
        </Link>
      </div>
    </div>
  );
}
