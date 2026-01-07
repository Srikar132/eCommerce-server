-- Check all categories and their slugs
SELECT 
    c.id,
    c.name,
    c.slug,
    c.parent_id,
    p.name as parent_name,
    p.slug as parent_slug,
    COUNT(prod.id) as product_count
FROM categories c
LEFT JOIN categories p ON c.parent_id = p.id
LEFT JOIN products prod ON prod.category_id = c.id
GROUP BY c.id, c.name, c.slug, c.parent_id, p.name, p.slug
ORDER BY c.name;

-- Find products with T-shirt categories
SELECT 
    p.id,
    p.name as product_name,
    c.name as category_name,
    c.slug as category_slug
FROM products p
JOIN categories c ON p.category_id = c.id
WHERE c.name LIKE '%T-Shirt%' OR c.slug LIKE '%tshirt%' OR c.slug LIKE '%t-shirt%'
ORDER BY c.slug, p.name;
