function AuthLayout({ title, subtitle, children }) {
  return (
    <div className="auth-page">
      <div className="auth-card">

        <h1>{title}</h1>

        <p>{subtitle}</p>

        {children}

      </div>
    </div>
  );
}

export default AuthLayout;